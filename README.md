# From OCR Tokens to Apparel Size Tables: An Android Implementation and Failure Analysis

[![Paper DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.23600826.svg)](https://doi.org/10.5281/zenodo.23600826)

**Read the paper:** [10-page PDF](paper/paper1_review.pdf) · [LaTeX source](paper/main.tex)

This public preprint is published on Zenodo ([DOI: 10.5281/zenodo.23600826](https://doi.org/10.5281/zenodo.23600826)), and has not been peer reviewed. Cite the paper for its analysis and this repository for code reuse. No journal publication is claimed.

A source-level study of Causyne's Android size-table OCR pipeline. Selected, unchanged Android implementation files are under `android-source/`; `ANDROID_SOURCE_MANIFEST.json` records their original paths and hashes. They are a source snapshot, not a standalone Android app.

The separate Kotlin/JVM prototype under `src/` groups pre-extracted spatial OCR tokens and has runnable unit tests (`./gradlew test`, JDK 17). The prototype is not the production Android pipeline. Neither these tests nor the source snapshot establish measured OCR accuracy or device performance.

The manuscript is in [`paper/`](paper/) and is a working preprint, not a peer-reviewed publication. Its author is Ikbal Fadilah ([ORCID](https://orcid.org/0009-0008-4853-135X)).

The original code is licensed under [MIT](LICENSE). The manuscript and its PDF are licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Reuse of the manuscript requires attribution. Please cite the paper when using its findings; the repository's [`CITATION.cff`](CITATION.cff) describes the research code.
