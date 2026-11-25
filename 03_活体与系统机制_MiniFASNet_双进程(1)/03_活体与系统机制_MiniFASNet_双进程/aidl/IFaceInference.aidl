// IFaceInference.aidl
// Keep this file ASCII-only: the AIDL compiler reads it with the platform
// default charset on Windows and fails on non-ASCII bytes.
package com.example.osai;

import com.example.osai.FaceFrame;
import com.example.osai.FrameResult;

interface IFaceInference {
    /** Submit one upright JPEG frame and block until the pipeline finishes. */
    FrameResult analyzeFrame(in FaceFrame frame);

    /** Register the last detected face under the given label. */
    boolean registerCurrent(String label);

    String[] listFaces();

    boolean deleteFace(String label);

    boolean setIdentifyEnabled(boolean enabled);

    boolean setLivenessEnabled(boolean enabled);

    void setThreadCount(int count);

    /** One-line status string. */
    String getStatus();

    /** JSON snapshot used by the system status page. */
    String getDebugSnapshot();

    void resetStats();

    /** Kill the inference process on purpose, to test death/reconnect handling. */
    void crashForTest();
}
