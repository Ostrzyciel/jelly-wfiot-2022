package pl.ostrzyciel.superfast_jellyfish.convert

import pl.ostrzyciel.superfast_jellyfish.convert.RDFStreamOptions
import pl.ostrzyciel.superfast_jellyfish.proto.*

import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.jdk.CollectionConverters.*

class NameEncoder(opt: RDFStreamOptions, rowsBuffer: ListBuffer[RDF_StreamRow]):
  private val nameLookup = new EncoderLookup(opt.nameTableSize)
  private val prefixLookup = new EncoderLookup(opt.prefixTableSize)
  private val dtLookup = new EncoderLookup(opt.datatypeTableSize)
  private val dtTable = new DecoderLookup[RDF_Literal.LiteralKind.Datatype](opt.datatypeTableSize)

  /**
   * Try to extract the prefix out of the IRI.
   *
   * Somewhat based on [[org.apache.jena.riot.system.PrefixMapStd.getPossibleKey]]
   * @param iri IRI
   * @return prefix or null (micro-op, don't hit me)
   */
  private def getIriPrefix(iri: String): String =
    iri.lastIndexOf('#') match
      case i if i > -1 => iri.substring(0, i + 1)
      case _ =>
        iri.lastIndexOf('/') match
          case i if i > -1 => iri.substring(0, i + 1)
          case _ => null

  def encodeIri(iri: String): RDF_IRI =
    def plainIriEncode: RDF_IRI =
      nameLookup.addEntry(iri) match
        case EncoderValue(id, false) =>
          RDF_IRI(nameId = id)
        case EncoderValue(id, true) =>
          rowsBuffer.append(
            RDF_StreamRow(RDF_StreamRow.Row.Name(
              RDF_NameRow(id = id, value = iri)
            ))
          )
          RDF_IRI(nameId = id)

    if opt.prefixTableSize == 0 then
      // Use a lighter algorithm if the prefix table is disabled
      return plainIriEncode

    getIriPrefix(iri) match
      case null => plainIriEncode
      case prefix =>
        val postfix = iri.substring(prefix.length)
        val pVal = prefixLookup.addEntry(prefix)
        val iVal = if postfix.nonEmpty then nameLookup.addEntry(postfix) else EncoderValue(0, false)

        if pVal.newEntry then rowsBuffer.append(
          RDF_StreamRow(RDF_StreamRow.Row.Prefix(
            RDF_PrefixRow(pVal.id, prefix)
          ))
        )
        if iVal.newEntry then rowsBuffer.append(
          RDF_StreamRow(RDF_StreamRow.Row.Name(
            RDF_NameRow(iVal.id, postfix)
          ))
        )
        RDF_IRI(prefixId = pVal.id, nameId = iVal.id)

  def encodeDatatype(dt: String): RDF_Literal.LiteralKind.Datatype =
    val dtVal = dtLookup.addEntry(dt)
    if dtVal.newEntry then
      dtTable.update(
        dtVal.id,
        RDF_Literal.LiteralKind.Datatype(RDF_Datatype(dtVal.id))
      )
      rowsBuffer.append(
        RDF_StreamRow(RDF_StreamRow.Row.Datatype(
          RDF_DatatypeRow(id = dtVal.id, value = dt)
        ))
      )
    dtTable.get(dtVal.id)
