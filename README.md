# Yolo 26-Local-Pose-Analyzer

![Demo](./app/src/main/res/raw/ss_1.gif)

# ✅ Yolo 26 pose Model Summary

```text
* **Input:**
    Shape: [1, 640, 640, 3]
    Type: FLOAT32
    Quant: scale=0.0, zeroPoint=0 
* **Output:**
     Shape: [1, 300, 57]
     Type: FLOAT32
     Quant: scale=0.0, zeroPoint=0
```
# coco pose output format

https://github.com/ultralytics/ultralytics/blob/main/ultralytics/cfg/datasets/coco-pose.yaml

```text
57 = 4 (box) + 1 (object confidence) + 1 (class) + 51 (keypoints)
```

--- 
* the model have **300 candidate detections**
* Each detection has **57 values**
---

# 🧠 What does the `57` mean?

```text
57 = 4 (box) + 1 (object confidence) + 1 (class) + 51 (keypoints)
```

Since:

* 17 keypoints × 3 values = **51**

---

## 📦 Final structure per detection:

```text
[0]  x_center
[1]  y_center
[2]  width
[3]  height
[4]  object_conf
[5]  class_conf (or class index depending on export)

[6 ... 56] = keypoints
```
# Keypoint names per class
```
kpt_names:
0:
  - nose
  - left_eye
  - right_eye
  - left_ear
  - right_ear
  - left_shoulder
  - right_shoulder
  - left_elbow
  - right_elbow
  - left_wrist
  - right_wrist
  - left_hip
  - right_hip
  - left_knee
  - right_knee
  - left_ankle
  - right_ankle
```
---

## 🔑 Keypoints structure

Each keypoint uses **3 values**:

```text
kpt_x, kpt_y, kpt_conf
```

So:

```text
kpt0 → [6,7,8]
kpt1 → [9,10,11]
...
kpt16 → [54,55,56]
```


---

# ⚙️ How to read the output buffer

### ✅ Correct buffer setup

```java
float[][][] output = new float[1][300][57];

tflite.run(inputBuffer, output);
```

---

# 🔍 Decoding loop (FULL example)

```java
for (int i = 0; i < 300; i++) {

    float objConf = output[0][i][4];

    if (objConf < 0.5f) continue; // filter weak detections

    float cx = output[0][i][0];
    float cy = output[0][i][1];
    float w  = output[0][i][2];
    float h  = output[0][i][3];

    // Convert center → box corners
    float x1 = cx - w / 2;
    float y1 = cy - h / 2;
    float x2 = cx + w / 2;
    float y2 = cy + h / 2;

    // --- KEYPOINTS ---
    int kptStart = 6;

    List<Keypoint> keypoints = new ArrayList<>();

    for (int k = 0; k < 17; k++) {

        float kx = output[0][i][kptStart + k * 3];
        float ky = output[0][i][kptStart + k * 3 + 1];
        float kc = output[0][i][kptStart + k * 3 + 2];

        if (kc > 0.5f) {
            keypoints.add(new Keypoint(kx, ky, kc));
        }
    }

    // Save detection
}
```

---

# ⚠️ VERY IMPORTANT: Coordinate format

Now the critical question:

### Are values normalized OR pixel-based?

Check by printing:

```java
Log.d("VAL", "cx=" + cx + ", cy=" + cy);
```

### Case A: Values between 0–1

👉 normalized → multiply by 640

```java
cx *= 640;
cy *= 640;
w  *= 640;
h  *= 640;
```

---

### Case B: Values around 0–640

👉 already pixel space → DO NOTHING

---

# 📱 Mapping to screen

If your preview is not 640×640:

```java
float scaleX = viewWidth / 640f;
float scaleY = viewHeight / 640f;

float screenX = modelX * scaleX;
float screenY = modelY * scaleY;
```

---

# 🎨 Drawing (quick example)

```java
// Box
canvas.drawRect(x1, y1, x2, y2, boxPaint);

// Keypoints
for (Keypoint kp : keypoints) {
    canvas.drawCircle(kp.x, kp.y, 6, keypointPaint);
}
```

---

# 🧩 Optional: Draw skeleton (recommended)

Example pairs (COCO):

```text
(5,7), (7,9)   // left arm
(6,8), (8,10)  // right arm
(11,13), (13,15) // left leg
(12,14), (14,16) // right leg
```

---

# ✅ Final mental model

Each of your 300 rows =

👉 **One person detection with 17 body joints**

---
```text
CMF: 
INTEGER_QUANT:
    cpu: fps-4.5-5f/s, inference time - 160-200ms
    gpu: fps-3-3.5f/s, inference time - 200-250ms
    nnapi: fps-4-4.5f/s, inference. time - 200-250ms

INT_8:(all delegate performs similar result)
    cpu: fps-2.7-3f/s, inference time - 310-350ms
    gpu: fps-2.6-3f/s, inference time - 310-350ms
    nnapi: fps-2.7-3f/s, inference. time - 310-350ms

Float_32:
    cpu: fps-2-2.3f/s, inference time - 420-460ms
    gpu: fps-1.5-1.7f/s, inference time - 580-610ms
    nnapi: fps-1.5-1.7f/s, inference time - 580-610ms
```
---
```text
pixel 6a: 
INTEGER_QUANT:
    cpu: fps-3.5-4.2f/s, inference time - 160-200ms
    gpu: fps-2.2-2.5f/s, inference time - 210-240ms
    nnapi: crashes

INT_8:(all delegate performs similar result)
    cpu:  
    gpu:  
    nnapi:  

Float_32:
    cpu:  
    gpu:  
    nnapi:  
```

pixel 6a: NNAPI error:
 - in simple term this model does not support fully nnapi operation. 
 - NNAPI does NOT support all TensorFlow Lite ops
 - Typical problematic ops:
    - Custom ops (YOLO often has these)
    - Dynamic shapes
    - Certain activations (e.g. mish, swish variants)
    - Post-processing ops (NMS, etc.)
 - partial operation combined with cpu is only possible.
```bash

05:58:19.793 tflite                  com.example.yolo26localposeanalyzer  W  NNAPI SL driver did not implement SL_ANeuralNetworksDiagnostic_registerCallbacks!
05:58:19.796 tflite                  com.example.yolo26localposeanalyzer  I  Replacing 29 out of 482 node(s) with delegate (TfLiteNnapiDelegate) node, yielding 7 partitions for the whole graph.
05:58:19.796 tflite                  com.example.yolo26localposeanalyzer  W  NNAPI SL driver did not implement SL_ANeuralNetworksDiagnostic_registerCallbacks!
05:58:19.796 tflite                  com.example.yolo26localposeanalyzer  W  NNAPI SL driver did not implement SL_ANeuralNetworksDiagnostic_registerCallbacks!
05:58:20.324 ExecutionPlan           com.example.yolo26localposeanalyzer  I  ExecutionPlan::SimpleBody::finish: compilation finished successfully on google-edgetpu
05:58:20.907 ExecutionPlan           com.example.yolo26localposeanalyzer  I  ExecutionPlan::SimpleBody::finish: compilation finished successfully on google-edgetpu
IDevice::prepareModel() error: hardware/interfaces/neuralnetworks/aidl/utils/src/Callbacks.cpp:43: model preparation failed with GENERAL_FAILURE
05:58:24.415 ComposeInternal         com.example.yolo26localposeanalyzer  E  Error was captured in composition.
                                                                             java.lang.IllegalArgumentException: Internal error: Failed to apply delegate: NN API returned error ANEURALNETWORKS_OP_FAILED at line 4768 while completing NNAPI compilation.
                                                                             
                                                                             Node number 484 (TfLiteNnapiDelegate) failed to prepare.
                                                                             Restored original execution plan after delegate application failure.
                                                                             	at org.tensorflow.lite.NativeInterpreterWrapper.createInterpreter(Native Method)

```