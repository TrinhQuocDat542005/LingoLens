# Model evaluation protocol

## Model under test

- Engine: MediaPipe Tasks Vision Object Detector
- Model: EfficientDet-Lite0 INT8, COCO labels
- Asset: `app/src/main/assets/efficientdet_lite0.tflite`
- SHA-256: `0720BF247BD76E6594EA28FA9C6F7C5242BE774818997DBBEFFC4DA460C723BB`
- Score threshold: `0.35`
- Maximum detections: `10`
- Fallback: bundled ML Kit Image Labeling

## Protocol

1. Use the same physical device for every comparison.
2. Restart the app before the first test and record device model/Android version.
3. Capture each scene five times without changing the expected classes.
4. Record every detected label, top confidence, inference time and whether all expected objects were found.
5. Repeat the suite for good light, low light and a complex background.
6. Do not count manual selection as a correct model prediction.

## Metrics

```text
precision = true_positive / (true_positive + false_positive)
recall    = true_positive / (true_positive + false_negative)
f1        = 2 * precision * recall / (precision + recall)
```

Acceptance targets:

- Precision >= 0.75 for supported COCO classes.
- Recall >= 0.70 for supported COCO classes.
- Median inference <= 800 ms on the reference phone.
- At least three supported objects detected in a suitable multi-object scene.
- No crash for empty, dark, blurry or unsupported scenes.

Populate `device_test_results.csv` from the values displayed on Result. Physical measurements must not be fabricated by automated tests.
