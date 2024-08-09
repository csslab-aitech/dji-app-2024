#pragma version(1)
#pragma rs java_package_name(com.dji.sdk.sample)

uchar4 __attribute__((kernel)) yuvToRgb(uchar4 in, uint32_t x, uint32_t y) {
    uchar Y = in.y;
    uchar U = in.z - 128;
    uchar V = in.w - 128;

    float R = Y + 1.402f * V;
    float G = Y - 0.344f * U - 0.714f * V;
    float B = Y + 1.772f * U;

    uchar4 out;
    out.r = clamp(R, 0.0f, 255.0f);
    out.g = clamp(G, 0.0f, 255.0f);
    out.b = clamp(B, 0.0f, 255.0f);
    out.a = 255;
    return out;
}
