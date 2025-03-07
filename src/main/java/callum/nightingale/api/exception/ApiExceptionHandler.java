package callum.nightingale.api.exception;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class ApiExceptionHandler {

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler({
      MethodArgumentTypeMismatchException.class,
      MethodArgumentNotValidException.class,
      MissingRequestHeaderException.class,
      HttpMessageNotReadableException.class,
  })
  public ExceptionResponse badRequest(Exception ex) {
    log.error("Bad Request (400): ", ex);
    return new ExceptionResponse(ErrorCodes.INVALID_PAYLOAD.name(),
        ErrorCodes.INVALID_PAYLOAD.getReason());
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler({
      NotFoundException.class
  })
  public ExceptionResponse notFound(Exception ex) {
    log.error("Not Found (404): ", ex);
    return new ExceptionResponse(ErrorCodes.NOT_FOUND.name(), ex.getMessage());
  }

  @ResponseStatus(HttpStatus.FORBIDDEN)
  @ExceptionHandler({
      ForbiddenException.class
  })
  public ExceptionResponse forbidden(Exception ex) {
    log.error("Forbidden (403): ", ex);
    return new ExceptionResponse(ErrorCodes.FORBIDDEN.name(), ex.getMessage());
  }

  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler
  public ExceptionResponse serverError(Exception ex) {
    log.error("Server Error (500): ", ex);
    return new ExceptionResponse(ErrorCodes.SERVER_ERROR.name(),
        ErrorCodes.SERVER_ERROR.getReason());
  }

  @Getter
  @AllArgsConstructor
  private enum ErrorCodes {
    INVALID_PAYLOAD("Submission has not passed validation. Invalid payload."),
    SERVER_ERROR("AWS Client is currently experiencing problems that require live service intervention."),
    FORBIDDEN("You are forbidden to perform the action requested."),
    CONFLICT("There has been a conflict whilst making the requested updates."),
    NOT_FOUND("The entity you are searching for is not found.");

    private final String reason;
  }

  @AllArgsConstructor
  public static class ExceptionResponse {

    @Getter
    private String code;

    @Getter
    private String reason;
  }


}
