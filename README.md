[![DOI](https://zenodo.org/badge/508663926.svg)](https://zenodo.org/badge/latestdoi/508663926)

This repository contains supplementary materials to the article titled "**Efficient RDF Streaming for the Edge-Cloud Continuum**". The publication is currently in review (**[preprint](https://arxiv.org/abs/2207.04439)**).

## Contents

### Extra tables and plots

The `extra` directory contains a PDF file with additional materials that could not fit into the paper. Also attached is the LaTeX source and the plots in the EPS format.

### Scala sources

The `superfast-jellyfish` directory contains a Scala project that implements the method described in the paper.

Namespaces in the project:
* `pl.ostrzyciel.superfast_jellyfish` – the main namespace, contains some utility classes and a simple implementation of both a streaming server and a streaming client.
  * `convert` – implementations for the encoder and decoder. These classes convert between the Protocol Buffers representation and Apache Jena's classes.
  * `stream` – Akka Streams flows for integrating the encoder and decoder in streaming applications.
  * `benchmark` – a set of classes that were used to conduct the experiments presented in the paper. It is recommended to run them with the [attached scripts](#Scripts).

To run the benchmarks, it is recommended to use a full JAR assembly. You can create one using the `sbt assembly` command. In the paper, the experiments were ran on [GraalVM Community 22.1](https://www.graalvm.org/downloads/).

See also: [sbt documentation](https://www.scala-sbt.org/), [sbt-assembly](https://github.com/sbt/sbt-assembly).

### Scripts

In the `scripts` directory you will find bash scripts that were used to operate the experiments. The datasets can be downloaded from [here](http://purl.org/net/ro-eri-ISWC14), keep them as separate `.nt.gz` files. The Flickr and Nevada datasets have to be trimmed to the first 10 million triples.

* `set_netem.sh` sets up network emulation needed for end-to-end tests. `network_notes.md` explains how to set this up.
* `kafka.properties` is the Kafka config that we've used. The important part is listeners – there needs to be three of them, for different speeds (as emulated by netem).
* `ser_des.sh` – raw ser/des throughput. Arguments: [java executable] [path to Jelly's JAR] [base directory containing the datasets]
* `size.sh` – serialized size. Arguments: [java executable] [path to Jelly's JAR] [base directory containing the datasets]
* `full_grpc.sh` – end-to-end gRPC streaming throughput. Arguments: [java executable] [path to Jelly's JAR] [base directory containing the datasets] [port over which to communicate]
* `kafka.sh` – end-to-end Kafka streaming throughput. Arguments: [java executable] [path to Jelly's JAR] [base directory containing the datasets] [port for the producer]
* `latency.sh` – end-to-end streaming latency. Arguments: [java executable] [path to Jelly's JAR] [base directory containing the datasets] [port for gRPC] [port for the Kafka producer]

Keep in mind that a lot of these benchmarks take a long time to run (in total it takes several days to complete the suite).

### Gathered measurements & data analysis

The `eda` directory contains the Jupyter notebooks that were used for data analysis and generation of tables and figures. Additionally, the `data.7z` file contains all raw measurements that were gathered in this study.

## Reproducibility

We hope that the provided materials are enough for interested researchers to reproduce the experiments. However, in case of any issues, don't hesitate to contact us (contact details are in the paper).

We are currently working on making Jelly into a fully-fledged set of open-source libraries. This implementation is thus subject to change.

## License

The materials in this repository are licensed under the Apache 2.0 License. You can cite the materials using this DOI: [![DOI](https://zenodo.org/badge/508663926.svg)](https://zenodo.org/badge/latestdoi/508663926)

## Authors

**[Piotr Sowiński](https://orcid.org/0000-0002-2543-9461)** (1, 2), [Katarzyna Wasielewska-Michniewska](https://orcid.org/0000-0002-3763-2373) (2), [Maria Ganzha](https://orcid.org/0000-0001-7714-4844) (1, 2), [Wiesław Pawłowski](https://orcid.org/0000-0002-5105-8873) (2, 3), [Paweł Szmeja](https://orcid.org/0000-0003-0869-3836) (2) [Marcin Paprzycki](https://orcid.org/0000-0002-8069-2152) (2)

* (1) Warsaw University of Technology
* (2) Systems Research Institute, Polish Academy of Sciences
* (3) Dept. of Mathematics, Physics, and Informatics, University of Gdańsk

## Acknowledgements

This work is part of the [ASSIST-IoT project](https://assist-iot.eu/) that has received funding from the EU’s Horizon 2020 research and innovation programme under grant agreement No 957258.

