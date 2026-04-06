const val LIQUID_GLASS_SHADER = """
    uniform shader background;
    uniform shader textMask;
    uniform float2 resolution;
    
    half4 main(float2 coord) {
        half4 mask = textMask.eval(coord);
        
        // We start the effect as soon as the mask begins (0.05)
        if (mask.a > 0.05) {
            
            // 1. THE BEND MATH
            // 'edgeFactor' is strongest at the edges and zero in the middle.
            float warpPower = 35.0; 
            float edgeFactor = pow(1.0 - mask.a, 1.5);
            float baseBend = edgeFactor * warpPower; 
            
            // 2. THE RAINBOW EFFECT (Chromatic Aberration)
            // We sample the background 3 times with different offsets for R, G, and B.
            // Red bends the most, Blue the least.
            float2 rCoord = coord + float2(baseBend * 1.2, baseBend * 1.2);
            float2 gCoord = coord + float2(baseBend * 1.0, baseBend * 1.0);
            float2 bCoord = coord + float2(baseBend * 0.8, baseBend * 0.8);
            
            half r = background.eval(rCoord).r;
            half g = background.eval(gCoord).g;
            half b = background.eval(bCoord).b;
            
            half4 glassColor = half4(r, g, b, 1.0);
            
            // 3. GLOSS & SHINE
            // Add a crisp white highlight on the "top" of the glass
            float shine = smoothstep(0.5, 0.9, mask.a) * 0.5;
            glassColor.rgb += half3(shine);
            
            return glassColor;
        } else {
            // Outside the font, show the normal background
            return background.eval(coord);
        }
    }
"""