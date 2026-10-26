# Clothing Size Recommendations from Sparse Body Measurements and Photographed Charts

This repository contains Ikbal Fadilah's [preprint on Zenodo](https://doi.org/10.5281/zenodo.23653430) and its companion source snapshot.

The [paper](paper/clothing-size-recommendations.pdf) explains the existing Android Size Guide's sparse-input, optional body-measurement, photographed-chart, and fit-ranking paths. It reports source-observed behavior and limitations. It does not claim measured fit accuracy.

`source/` contains selected **unchanged files from the original Android application**, preserving their original relative paths. `SOURCE_SHA256.txt` records their hashes. This is a research source snapshot, not a standalone Android project: application wiring, unrelated features, build configuration, private settings, model binaries, and third-party datasets are outside this release. The active route is traced in `SizeViewModel.kt`, `BodyEstimator.kt`, `OcrSizeIntelligenceEngine.kt`, and `SizeRecommendationEngine.kt`. Some other included components are historical or not used by that active route; the paper distinguishes them.

The paper is licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The selected original source is released under the [MIT License](LICENSE-CODE). Please cite the paper if you use its analysis, and retain the code license and copyright notice when reusing the source.

The earlier [size-table OCR study](https://github.com/ikbal-causyne/causyne-size-table-ocr) remains a separate publication.
