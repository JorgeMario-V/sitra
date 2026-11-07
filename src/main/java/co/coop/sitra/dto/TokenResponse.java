package co.coop.sitra.dto;

public record TokenResponse(String token, String tipo, long expiraEnSegundos) {
}
