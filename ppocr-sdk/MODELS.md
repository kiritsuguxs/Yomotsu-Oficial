# PaddleOCR provenance for Yomotsu Y13-test1

Y13-test1 vendors the official PaddleOCR Android SDK source from PaddlePaddle/PaddleOCR
commit `2661c7c0ef5c613e8f93c6e93b2e052399f0f854`. The copied source is the upstream
`deploy/ppocr-android/ppocr-sdk/src/main/java/com/paddle/ocr` tree and retains its
Apache License 2.0 headers. A copy of that license is included as `LICENSE`.

Only the official PP-OCRv5 Mobile detector and English recognition model are bundled.
There is no runtime download path and no recognition model for another language in
this test build.

| Purpose | Official archive | Archive SHA-256 | Bundled file | Size | Bundled SHA-256 |
| --- | --- | --- | --- | ---: | --- |
| Detection | [PP-OCRv5 mobile detector](https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/PP-OCRv5_mobile_det_onnx_infer.tar) | `781056046c9ed77a15c94681605db6a0f62317c2e9cce6931c71da2478d4bc30` | `src/main/assets/models/det/inference.onnx` | 4,826,518 bytes | `a431985659dc921974177a95adcfbb90fd9e51989a5e04d70d0b75f597b6e61d` |
| English recognition | [English PP-OCRv5 mobile recognizer](https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/en_PP-OCRv5_mobile_rec_onnx_infer.tar) | `4424e851309b291b00aab8191cd4314cefbd2d1b2381ff8994019d262fa95e28` | `src/main/assets/models/rec/inference.onnx` | 7,848,423 bytes | `b5f833dfc5d0eb71da397b4efa06ebeee9b431b690a47d6af40d77d8eabc557f` |
| English dictionary/config | Same English recognition archive | Same archive checksum | `src/main/assets/models/rec/inference.yml` | 3,964 bytes | `27e91d0582f40168aa218303c76e184bc78fa7a5d105aad0cfbad8458b441067` |

The PaddleOCR source and official models are distributed under Apache License 2.0.
