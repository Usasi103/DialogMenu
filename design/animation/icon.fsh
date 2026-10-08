layout(location = 8) flat in float dmAnimationAlpha;
bool dm_animation_fragment() {
#ifdef IS_GUI
    if (dmAnimationAlpha < 0.0) return false;
    vec4 color = texture(Sampler0, texCoord0);
    // Discard empty texels before the fade, so the last 10% does not pop off.
    if (color.a < 0.1 || dmAnimationAlpha <= 0.0) discard;
    color *= vertexColor * ColorModulator;
    color.a *= dmAnimationAlpha;
#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#else
    fragColor = calculateFinalColor(color);
#endif
    return true;
#else
    return false;
#endif
}
