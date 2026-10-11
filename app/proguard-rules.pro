# Tesseract4Android (receipt scanning) ships no R8 rules of its own. Its
# native code finds Java members by name (TessBaseAPI.onProgressValues, the
# native* entry points, Pix / Pixa handles), so R8 must not rename or drop
# them. The library is small (two packages), so it's kept whole.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
