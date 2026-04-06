package com.example.myface

const val LIQUID_GLASS_SHADER = """
    uniform shader background;
    uniform shader textMask;
    uniform float2 resolution;
    
    half4 main(float2 coord) {
        half4 mask = textMask.eval(coord);
        
        // Lowered the threshold slightly to catch the very edge of the blur
        if (mask.a > 0.05) { 
            
            // --- 1. THE CURVE ---
            // Using pow() makes the bend accelerate at the edges, 
            // simulating a true rounded droplet rather than a flat bevel.
            float warpPower = 45.0; 
            float edgeFactor = pow(1.0 - mask.a, 1.5); 
            float edgeBend = edgeFactor * warpPower; 
            
            // --- 2. CHROMATIC ABERRATION (Color Splitting) ---
            // We shift the Red, Green, and Blue channels slightly differently
            // to create a rainbow prism effect on the sharpest curves.
            float2 rOffset = float2(edgeBend * 1.15, edgeBend * 1.15);
            float2 gOffset = float2(edgeBend * 1.00, edgeBend * 1.00);
            float2 bOffset = float2(edgeBend * 0.85, edgeBend * 0.85);
            
            half r = background.eval(coord + rOffset).r;
            half g = background.eval(coord + gOffset).g;
            half b = background.eval(coord + bOffset).b;
            
            half4 glassColor = half4(r, g, b, 1.0);
            
            // --- 3. VOLUME & SHADING ---
            // Adds a subtle dark shadow right inside the edge to give the liquid visual weight/thickness
            float edgeShadow = smoothstep(0.0, 0.4, mask.a) * smoothstep(1.0, 0.6, mask.a);
            glassColor.rgb -= half3(edgeShadow * 0.25);
            
            // A brighter, sharper highlight to match the curved glass
            float shine = smoothstep(0.4, 0.9, mask.a) * 0.6; 
            glassColor.rgb += half3(shine);
            
            return glassColor;
        } else {
            // Draw normal background outside the text
            return background.eval(coord);
        }
    }
"""