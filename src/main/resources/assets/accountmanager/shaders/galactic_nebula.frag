// Galactic Nebula By: Aphrodite & Dxchx
#version 150

// --------------------------------------------------
// Uniforms
// --------------------------------------------------
uniform vec2 u_Resolution;   
uniform vec2 u_Mouse;        
uniform float u_Time;        

out vec4 fragColor;

// --------------------------------------------------
// Constants
// --------------------------------------------------
#define ITER 17              
#define VOLSTEPS 25          
#define ZOOM 0.8             
#define TILE 0.85            
#define SPEED 0.01           

#define BRIGHTNESS 0.002      
#define DARKMATTER 0.3       
#define DISTFADING 0.72      
#define STEPSIZE 0.1         

// --------------------------------------------------
// Helper Functions
// --------------------------------------------------

// Stable pseudo-random generator
float hash21(vec2 p){
    p = fract(p*vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// Small star-like specks (stable, no flicker)
float star(vec2 uv, float density){
    float h = hash21(floor(uv * density));
    return h;
}

// Nebula gradient: dynamic swirling purple/magenta with subtle dark blue
vec3 nebulaColor(vec3 c, vec3 pos, float time) {
    vec3 base = vec3(
        c.r*0.05 + 0.15, // red -> purple/magenta
        c.g*0.05 + 0.05, // green -> subtle magenta
        c.b*0.3 + 0.15   // blue -> dark blue hints
    );

    // Swirling highlights
    base.r += 0.06 * sin(time + pos.x*4.0 + pos.z*2.0);
    base.g += 0.05 * cos(time*1.3 + pos.y*5.0 + pos.z*3.0);
    base.b += 0.02 * sin(time*0.7 + pos.x*3.0 + pos.y*2.0);

    return base;
}

// --------------------------------------------------
// Main Shader
// --------------------------------------------------
void main() {
    vec2 uv = gl_FragCoord.xy / u_Resolution.xy - 0.5;
    uv.y *= u_Resolution.y / u_Resolution.x;

    vec3 dir = normalize(vec3(uv * ZOOM, 1.0));
    float time = u_Time * SPEED + 0.25;

    // Mouse rotation
    float a1 = 0.5 + u_Mouse.x / u_Resolution.x * 2.0;
    float a2 = 0.8 + u_Mouse.y / u_Resolution.y * 2.0;
    float c1 = cos(a1), s1 = sin(a1);
    float c2 = cos(a2), s2 = sin(a2);

    float dx = dir.x * c1 + dir.z * s1;
    float dz = -dir.x * s1 + dir.z * c1;
    dir.x = dx; dir.z = dz;

    float dx2 = dir.x * c2 + dir.y * s2;
    float dy2 = -dir.x * s2 + dir.y * c2;
    dir.x = dx2; dir.y = dy2;

    // Camera position
    vec3 from = vec3(1.0, 0.5, 0.5) + vec3(time*2.0, time, -2.0);

    float fx = from.x * c1 + from.z * s1;
    float fz = -from.x * s1 + from.z * c1;
    from.x = fx; from.z = fz;

    float fx2 = from.x * c2 + from.y * s2;
    float fy2 = -from.x * s2 + from.y * c2;
    from.x = fx2; from.y = fy2;

    // Volumetric fractal rendering
    vec3 col = vec3(0.0);
    float fade = 1.0;
    float s = 0.1;

    for (int r = 0; r < VOLSTEPS; r++) {
        vec3 p = from + dir * s * 0.5;

        // Tiling fold
        p.x = abs(TILE - mod(p.x, TILE*2.0));
        p.y = abs(TILE - mod(p.y, TILE*2.0));
        p.z = abs(TILE - mod(p.z, TILE*2.0));

        // Subtle swirling motion
        p.x += 0.02 * sin(time + p.z*2.0);
        p.y += 0.02 * cos(time*1.2 + p.x*2.0);
        p.z += 0.02 * sin(time*0.7 + p.y*2.0);

        float a = 0.0;
        float pa = 0.0;

        for (int i = 0; i < ITER; i++) {
            float l2 = dot(p,p);
            p = abs(p)/l2 - 0.53;
            a += abs(length(p) - pa);
            pa = length(p);
        }

        float dm = max(0.0, DARKMATTER - a*a*0.001);
        a = a*a*a;

        if (r > 6) fade *= 1.0 - dm;

        col += vec3(s, s*s, s*s*s*s) * a * BRIGHTNESS * fade;
        fade *= DISTFADING;
        s += STEPSIZE;
    }

    // Apply dynamic nebula color
    col = nebulaColor(col, from, u_Time);

    // --------------------------------------------------
    // Stable stars
    // --------------------------------------------------
    float st = star(gl_FragCoord.xy, 20.0); // density = 20
    col += vec3(0.9, 0.4, 0.8) * st * 0.02;

    // Dark purple background (#28182e)
    vec3 bg = vec3(0.156, 0.094, 0.18);
    col = mix(bg, col, 0.95);

    // Final adjustments
    col *= 0.035;
    col = pow(col, vec3(0.6));

    fragColor = vec4(col, 1.0);
}