package com.freightfox.chatapp.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain and validation failures to RFC 7807 {@link ProblemDetail} responses.
 *
 * <table>
 *   <caption>Error contract</caption>
 *   <tr><th>Condition</th><th>Status</th></tr>
 *   <tr><td>Duplicate room name</td><td>409 Conflict</td></tr>
 *   <tr><td>Missing room (join/send/history/delete)</td><td>404 Not Found</td></tr>
 *   <tr><td>Bean-validation failure</td><td>400 Bad Request</td></tr>
 *   <tr><td>Bad request argument (e.g. {@code limit <= 0})</td><td>400 Bad Request</td></tr>
 * </table>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateRoomNameException.class)
    public ProblemDetail handleDuplicateRoom(DuplicateRoomNameException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(RoomNotFoundException.class)
    public ProblemDetail handleRoomNotFound(RoomNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(ChatException.class)
    public ProblemDetail handleChat(ChatException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}