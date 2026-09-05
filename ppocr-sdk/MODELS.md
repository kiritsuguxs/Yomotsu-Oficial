# PaddleOCR provenance for Yomotsu Y22 experimental

Y13-test1 vendors the official PaddleOCR Android SDK source from PaddlePaddle/PaddleOCR
commit `2661c7c0ef5c613e8f93c6e93b2e052399f0f854`. The copied source is the upstream
`deploy/ppocr-android/ppocr-sdk/src/main/java/com/paddle/ocr` tree and retains its
Apache License 2.0 headers. A copy of that license is included as `LICENSE`.

Only the official PP-OCRv6 Small detector and multilingual recognizer are bundled.
The matching official recognition dictionary/config is bundled unchanged. There is
no runtime download path. SDK preprocessing, grouping and postprocessing are unchanged.

| Purpose | Official archive | Archive SHA-256 | Bundled file | Size | Bundled SHA-256 |
| --- | --- | --- | --- | ---: | --- |
| Detection | [PP-OCRv6_small_det_onnx_infer](https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/PP-OCRv6_small_det_onnx_infer.tar) | `d218f6fbf0f1c23d2161bd6ac7f5eaa6104fa89955c09290497e31008e2618e4` | `src/main/assets/models/det/inference.onnx` | 9,880,512 bytes | `d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e` |
| Recognition | [PP-OCRv6_small_rec_onnx_infer](https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/PP-OCRv6_small_rec_onnx_infer.tar) | `d267ab077a44a0eedb1ea8f8c542d263f211de8e9d7a029bf9fcfff7e5a88fb1` | `src/main/assets/models/rec/inference.onnx` | 21,159,378 bytes | `5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634` |
| Recognition dictionary/config | [PP-OCRv6_small_rec_onnx_infer](https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/PP-OCRv6_small_rec_onnx_infer.tar) | `d267ab077a44a0eedb1ea8f8c542d263f211de8e9d7a029bf9fcfff7e5a88fb1` | `src/main/assets/models/rec/inference.yml` | 150,579 bytes | `ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1` |

The PaddleOCR source and official models are distributed under Apache License 2.0.
