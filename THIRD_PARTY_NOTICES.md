The fixed-camera sampling design was studied in ArcMenu (commit
17cd17c1dba07cf99fc8b9055bb99e63220420e8). The Java fullscreen backend is independently implemented for Paper 26.3
and uses the shared DialogMenu action API.
Its single-space TextDisplay rectangle calibration uses the constants and
transformation from ArcMenu / FluxUI, retained only for the legacy comparison mode.
No third-party artwork is included.

The local-cursor shader and view-angle mapping were independently implemented.
The public resource pack uses vanilla Minecraft 26.3 shaders as its base.
No private server resources or HUD code are included.

ArcMenu: https://github.com/FENTAIIII/ArcMenu

MIT License

Copyright (c) 2026 FENTAIIII
Copyright (c) 2026 wiyuka (FluxUI rectangle calibration)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
