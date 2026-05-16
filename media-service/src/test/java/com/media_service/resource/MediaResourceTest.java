package com.media_service.resource;

import com.media_service.entity.MediaFile;
import com.media_service.service.MediaService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MediaResource — unit tests")
class MediaResourceTest {

    @Mock
    private MediaService mediaService;

    @InjectMocks
    private MediaResource sut;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sut, "localPath", tempDir.toString());
    }

    // ── upload ────────────────────────────────────────────────────────

    @Test
    @DisplayName("upload() — image content-type delegates to uploadImage")
    void upload_image_delegatesToUploadImage() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "bytes".getBytes());
        MediaFile expected = MediaFile.builder().mediaId("m1").build();
        when(mediaService.uploadImage(any(), eq("u1"), eq("r1"), isNull())).thenReturn(expected);

        ResponseEntity<MediaFile> resp = sut.upload(file, "u1", null, "r1", null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getMediaId()).isEqualTo("m1");
        verify(mediaService).uploadImage(any(), eq("u1"), eq("r1"), isNull());
    }

    @Test
    @DisplayName("upload() — non-image content-type delegates to uploadFile")
    void upload_nonImage_delegatesToUploadFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "data".getBytes());
        MediaFile expected = MediaFile.builder().mediaId("m2").build();
        when(mediaService.uploadFile(any(), eq("u1"), eq("r1"), eq("msg-1"))).thenReturn(expected);

        ResponseEntity<MediaFile> resp = sut.upload(file, "u1", null, "r1", "msg-1");

        assertThat(resp.getBody().getMediaId()).isEqualTo("m2");
        verify(mediaService).uploadFile(any(), eq("u1"), eq("r1"), eq("msg-1"));
    }

    @Test
    @DisplayName("upload() — falls back to uploaderIdParam when header is null")
    void upload_fallbackToParam() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.txt", "text/plain", "data".getBytes());
        when(mediaService.uploadFile(any(), eq("param-user"), eq("r1"), isNull()))
                .thenReturn(MediaFile.builder().mediaId("m3").build());

        sut.upload(file, null, "param-user", "r1", null);

        verify(mediaService).uploadFile(any(), eq("param-user"), eq("r1"), isNull());
    }

    @Test
    @DisplayName("upload() — falls back to 'anonymous' when both header and param are null")
    void upload_fallbackToAnonymous() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.txt", "text/plain", "data".getBytes());
        when(mediaService.uploadFile(any(), eq("anonymous"), eq("r1"), isNull()))
                .thenReturn(MediaFile.builder().mediaId("m4").build());

        sut.upload(file, null, null, "r1", null);

        verify(mediaService).uploadFile(any(), eq("anonymous"), eq("r1"), isNull());
    }

    @Test
    @DisplayName("upload() — null content-type delegates to uploadFile")
    void upload_nullContentType_delegatesToUploadFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "unknown", null, "data".getBytes());
        when(mediaService.uploadFile(any(), anyString(), anyString(), any()))
                .thenReturn(MediaFile.builder().mediaId("m5").build());

        ResponseEntity<MediaFile> resp = sut.upload(file, "u1", null, "r1", null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(mediaService).uploadFile(any(), eq("u1"), eq("r1"), isNull());
    }

    // ── getByRoom ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getByRoom() — returns 200 with file list")
    void getByRoom_returns200() {
        MediaFile f = MediaFile.builder().mediaId("m1").build();
        when(mediaService.getFilesByRoom("r1")).thenReturn(List.of(f));

        ResponseEntity<List<MediaFile>> resp = sut.getByRoom("r1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("getByRoom() — returns empty list when no files in room")
    void getByRoom_empty() {
        when(mediaService.getFilesByRoom("r-empty")).thenReturn(List.of());
        assertThat(sut.getByRoom("r-empty").getBody()).isEmpty();
    }

    // ── getByUser ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getByUser() — returns 200 with files for user")
    void getByUser_returns200() {
        when(mediaService.getFilesByUploader("u1")).thenReturn(
                List.of(MediaFile.builder().mediaId("m1").build()));

        ResponseEntity<List<MediaFile>> resp = sut.getByUser("u1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
    }

    // ── getById ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getById() — returns 200 when found")
    void getById_found_returns200() {
        MediaFile f = MediaFile.builder().mediaId("m1").build();
        when(mediaService.getFileById("m1")).thenReturn(Optional.of(f));

        ResponseEntity<MediaFile> resp = sut.getById("m1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getMediaId()).isEqualTo("m1");
    }

    @Test
    @DisplayName("getById() — returns 404 when not found")
    void getById_notFound_returns404() {
        when(mediaService.getFileById("missing")).thenReturn(Optional.empty());

        ResponseEntity<MediaFile> resp = sut.getById("missing");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── delete ────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete() — returns 204 No Content")
    void delete_returns204() throws IOException {
        doNothing().when(mediaService).deleteFile("m1");

        ResponseEntity<Void> resp = sut.delete("m1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(mediaService).deleteFile("m1");
    }

    // ── getCount ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getCount() — returns count from service")
    void getCount_returnsCount() {
        when(mediaService.getFileCount("r1")).thenReturn(7L);

        ResponseEntity<Long> resp = sut.getCount("r1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo(7L);
    }

    // ── view ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("view() — returns 200 OK with full file bytes")
    void view_fullFile_returns200() throws IOException {
        byte[] content = "video content".getBytes();
        Path file = tempDir.resolve("test-video.mp4");
        Files.write(file, content);

        HttpHeaders headers = new HttpHeaders();
        ResponseEntity<byte[]> resp = sut.view("test-video.mp4", headers);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo(content);
    }

    @Test
    @DisplayName("view() — returns 206 Partial Content for Range request")
    void view_rangeRequest_returns206() throws IOException {
        byte[] content = "0123456789".getBytes();
        Path file = tempDir.resolve("video.mp4");
        Files.write(file, content);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RANGE, "bytes=0-4");

        ResponseEntity<byte[]> resp = sut.view("video.mp4", headers);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(resp.getBody()).hasSize(5);
    }

    @Test
    @DisplayName("view() — returns 404 when file does not exist")
    void view_notFound_returns404() throws IOException {
        HttpHeaders headers = new HttpHeaders();
        ResponseEntity<byte[]> resp = sut.view("nonexistent.mp4", headers);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("view() — returns 400 when path traversal attempted")
    void view_pathTraversal_returns400() throws IOException {
        HttpHeaders headers = new HttpHeaders();
        ResponseEntity<byte[]> resp = sut.view("../secret.txt", headers);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── viewThumb ─────────────────────────────────────────────────────

    @Test
    @DisplayName("viewThumb() — returns 200 OK with thumbnail bytes")
    void viewThumb_found_returns200() throws IOException {
        Path thumbsDir = tempDir.resolve("thumbs");
        Files.createDirectories(thumbsDir);
        byte[] content = "thumb content".getBytes();
        Files.write(thumbsDir.resolve("img.jpg"), content);

        ResponseEntity<byte[]> resp = sut.viewThumb("img.jpg");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo(content);
    }

    @Test
    @DisplayName("viewThumb() — returns 404 when thumbnail does not exist")
    void viewThumb_notFound_returns404() throws IOException {
        ResponseEntity<byte[]> resp = sut.viewThumb("missing.jpg");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("viewThumb() — returns 400 when path traversal attempted")
    void viewThumb_pathTraversal_returns400() throws IOException {
        ResponseEntity<byte[]> resp = sut.viewThumb("../../etc/passwd");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
