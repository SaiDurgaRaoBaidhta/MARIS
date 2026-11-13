# egl-catalog-generator

Dev-time tool. Reads the EGL source **read-only** (default: ~/Desktop/MARIS/MarisProd-1) and writes the
record layouts and program catalog into `../../src/main/resources/legacy-catalog/`.

Inputs:
- `MARIS_Common/EGLSource/**/*Records.egl`, `CommonRecords.egl` – record definitions
- `MARIS_RUI/EGLSource/com/bart/maris/ui/start/ProgramServiceLib.egl` – program -> service + parameter list

It is not packaged into the middleware jar and never runs in production.
