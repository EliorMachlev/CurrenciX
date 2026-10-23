# Tesseract's JNI code calls back into these classes by name.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }

# The Play app loads the recognizer by name once its feature module is installed.
-keep class com.eliormachlev.currencix.ocr.engine.TesseractRecognizer {
    public <init>();
    public java.lang.String recognize(android.graphics.Bitmap, java.lang.String, java.lang.String);
}
