package com.fbcorp.gleo.service;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

@Service
public class QrDecoderService {

    private final MultiFormatReader reader;

    public QrDecoderService() {
        reader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, java.util.List.of(BarcodeFormat.QR_CODE));
        reader.setHints(hints);
    }

    public Optional<String> decode(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream in = file.getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) return Optional.empty();
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = reader.decode(bitmap);
            return Optional.ofNullable(result.getText());
        } catch (IOException | NotFoundException e) {
            return Optional.empty();
        }
    }
}
