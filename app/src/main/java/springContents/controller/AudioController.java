package springContents.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import springContents.service.S3Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audio")
public class AudioController {

    @Autowired
    private S3Service s3Service;

    /**
     * Get list of all audio files
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listAudioFiles() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<String> audioFiles = s3Service.listAudioFiles();
            response.put("success", true);
            response.put("files", audioFiles);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to list audio files: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Stream audio file from S3
     */
    @GetMapping("/stream/{fileName:.+}")
    public ResponseEntity<InputStreamResource> streamAudio(@PathVariable String fileName) {
        try {
            // Decode the filename in case it has special characters
            String decodedFileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);

            ResponseInputStream<GetObjectResponse> s3Object = s3Service.getAudioFile(decodedFileName);

            // Determine content type based on file extension
            String contentType = getContentType(decodedFileName);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentDispositionFormData("inline", decodedFileName);
            headers.setCacheControl("public, max-age=3600");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(s3Object));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Stream audio file from a series bucket
     */
    @GetMapping("/series/{seriesId}/stream/{fileName:.+}")
    public ResponseEntity<InputStreamResource> streamSeriesAudio(
            @PathVariable Long seriesId,
            @PathVariable String fileName) {
        try {
            // Decode the filename in case it has special characters
            String decodedFileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);

            ResponseInputStream<GetObjectResponse> s3Object =
                    s3Service.getAudioFileFromSeriesBucket(seriesId, decodedFileName);

            // Determine content type based on file extension
            String contentType = getContentType(decodedFileName);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentDispositionFormData("inline", decodedFileName);
            headers.setCacheControl("public, max-age=3600");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(s3Object));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private String getContentType(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (lowerFileName.endsWith(".wav")) {
            return "audio/wav";
        } else if (lowerFileName.endsWith(".ogg")) {
            return "audio/ogg";
        } else if (lowerFileName.endsWith(".m4a")) {
            return "audio/mp4";
        } else if (lowerFileName.endsWith(".opus")) {
            return "audio/opus";
        } else if (lowerFileName.endsWith(".flac")) {
            return "audio/flac";
        } else if (lowerFileName.endsWith(".aac")) {
            return "audio/aac";
        } else if (lowerFileName.endsWith(".webm")) {
            return "audio/webm";
        } else if (lowerFileName.endsWith(".aiff") || lowerFileName.endsWith(".aif")) {
            return "audio/aiff";
        } else if (lowerFileName.endsWith(".wma")) {
            return "audio/x-ms-wma";
        }
        return "audio/mpeg"; // default
    }
}