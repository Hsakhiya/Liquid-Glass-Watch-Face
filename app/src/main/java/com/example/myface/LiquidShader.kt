package com.example.myface


const val LIQUID_GLASS_SHADER = """
    uniform shader background;
    uniform shader textMask;
    uniform float2 resolution;
    
    half4 main(float2 coord) {
        half4 mask = textMask.eval(coord);
        
        if (mask.a > 0.1) {
            float2 distortedCoord = coord;
            
            // --- THE LIQUID MAGIC ---
            // (1.0 - mask.a) means the distortion is STRONGEST at the edges 
            // and gets weaker in the thick center of the font.
            float warpPower = 45.0; // <--- Change this number to make it more/less extreme
            float edgeBend = (1.0 - mask.a) * warpPower; 
            
            distortedCoord.x += edgeBend; 
            distortedCoord.y += edgeBend;
            
            half4 glassColor = background.eval(distortedCoord);
            
            // A brighter, sharper highlight to match the curved glass
            float shine = smoothstep(0.4, 0.9, mask.a) * 0.5; 
            glassColor.rgb += half3(shine);
            
            return glassColor;
        } else {
            return background.eval(coord);
        }
    }
"""