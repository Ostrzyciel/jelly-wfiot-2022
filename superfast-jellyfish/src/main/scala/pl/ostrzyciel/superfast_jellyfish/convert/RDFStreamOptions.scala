package pl.ostrzyciel.superfast_jellyfish.convert

import com.typesafe.config.Config
import pl.ostrzyciel.superfast_jellyfish.proto.RDF_StreamOptions

object RDFStreamOptions:
  def apply(opt: RDF_StreamOptions): RDFStreamOptions =
    RDFStreamOptions(
      nameTableSize = opt.maxNameTableSize,
      prefixTableSize = opt.maxPrefixTableSize,
      datatypeTableSize = opt.maxDatatypeTableSize,
      useRepeat = opt.useRepeat,
    )

  def apply(config: Config): RDFStreamOptions =
    RDFStreamOptions(
      nameTableSize = config.getInt("jelly.name-table-size"),
      prefixTableSize = config.getInt("jelly.prefix-table-size"),
      datatypeTableSize = config.getInt("jelly.dt-table-size"),
      useRepeat = config.getBoolean("jelly.use-repeat"),
    )

/**
 * Represents the compression options for a protobuf RDF stream.
 * @param nameTableSize maximum size of the name table
 * @param prefixTableSize maximum size of the prefix table
 * @param datatypeTableSize maximum size of the datatype table
 * @param useRepeat whether or not to use RDF_REPEAT terms
 */
case class RDFStreamOptions(nameTableSize: Int = 4000, prefixTableSize: Int = 150, datatypeTableSize: Int = 32,
                            useRepeat: Boolean = true):
  /**
   * @return a stream options row to be included as a header in the stream
   */
  def toRDF: RDF_StreamOptions =
    RDF_StreamOptions(
      maxNameTableSize = nameTableSize,
      maxPrefixTableSize = prefixTableSize,
      maxDatatypeTableSize = datatypeTableSize,
      useRepeat = useRepeat,
    )
