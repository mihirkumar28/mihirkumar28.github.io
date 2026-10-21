package com.mihirkumar.dayflow;

import android.content.Context;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.*;

/** Verify that all catalog PDFs can be rendered from APK assets with airplane mode enabled. */
@RunWith(AndroidJUnit4.class)
public class OfflinePdfLibraryTest {
    @Test public void everyCatalogPdfOpensOffline() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PlanCatalog.initialize(context);

        Set<String> expected = new HashSet<>();
        for (PlanCatalog.Document document : PlanCatalog.documents()) {
            expected.add(document.filename);
            assertTrue("Expected an offline PDF copy or original for " + document.id,
                    document.offlinePdfCopyBundled || document.originalPdfBundled);
        }

        Set<String> found = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(context.getAssets().open("plan_pdfs.zip"))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String filename = new File(entry.getName()).getName();
                    assertTrue("Unexpected bundled PDF: " + filename, expected.contains(filename));
                    File local = new File(context.getCacheDir(), filename);
                    try (FileOutputStream out = new FileOutputStream(local, false)) {
                        int n;
                        while ((n = zip.read(buffer)) != -1) out.write(buffer, 0, n);
                    }
                    try (java.io.FileInputStream input = new java.io.FileInputStream(local)) {
                        byte[] header = new byte[5];
                        assertEquals("Expected a complete PDF header", 5, input.read(header));
                        assertEquals("%PDF-", new String(header, StandardCharsets.US_ASCII));
                    }
                    try (ParcelFileDescriptor pfd =
                                 ParcelFileDescriptor.open(local, ParcelFileDescriptor.MODE_READ_ONLY);
                         PdfRenderer renderer = new PdfRenderer(pfd)) {
                        assertTrue("PDF contains no renderable pages: " + filename, renderer.getPageCount() > 0);
                        try (PdfRenderer.Page page = renderer.openPage(0)) {
                            assertTrue("PDF page has no dimensions: " + filename,
                                    page.getWidth() > 0 && page.getHeight() > 0);
                        }
                    }
                    found.add(filename);
                }
                zip.closeEntry();
            }
        }
        assertEquals("Every catalog document must be included", expected, found);
    }
}
