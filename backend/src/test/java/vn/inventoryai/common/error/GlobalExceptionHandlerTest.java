package vn.inventoryai.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {
    @Test
    void unexpectedErrorsDoNotExposeInternalExceptionMessages() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleUnexpected(new RuntimeException("jdbc:mysql://secret-host/private-schema"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(response.getBody().message())
                .doesNotContain("secret-host", "private-schema")
                .contains("Reference:");
    }

    @Test
    void malformedRequestsDoNotLogOrReturnSensitiveParserDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleMalformedRequest(
                new HttpMessageNotReadableException(
                        "Malformed password=secret-token",
                        mock(HttpInputMessage.class)
                )
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid request");
        assertThat(response.getBody().message()).doesNotContain("secret-token");
    }

    @Test
    void oversizedUploadsReturnPayloadTooLarge() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleUploadTooLarge(new MaxUploadSizeExceededException(2_000_000));

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Uploaded file is too large");
    }
}
