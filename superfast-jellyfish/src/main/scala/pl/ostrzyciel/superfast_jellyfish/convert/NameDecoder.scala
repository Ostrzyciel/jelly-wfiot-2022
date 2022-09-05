package pl.ostrzyciel.superfast_jellyfish.convert

import pl.ostrzyciel.superfast_jellyfish.convert.RDFProtobufDeserializationError
import pl.ostrzyciel.superfast_jellyfish.proto.*

class NameDecoder(opt: RDFStreamOptions):
  private val prefixLookup = new DecoderLookup[String](opt.prefixTableSize)
  private val nameLookup = new DecoderLookup[String](opt.nameTableSize)

  inline def updateNames(nameRow: RDF_NameEntry): Unit =
    nameLookup.update(nameRow.id, nameRow.value)

  inline def updatePrefixes(prefixRow: RDF_PrefixEntry): Unit =
    prefixLookup.update(prefixRow.id, prefixRow.value)

  def decode(iri: RDF_IRI): String =
    val prefix = iri.prefixId match
      case 0 => ""
      case id => prefixLookup.get(id)
    val name = iri.nameId match
      case 0 => ""
      case id => nameLookup.get(id)

    prefix + name
