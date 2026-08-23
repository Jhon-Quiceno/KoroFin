package com.korofin.backend.ai.service.provider;

/**
 * Un turno único enviado a {@link AiChatClient}, en la forma OpenAI-compatible
 * {@code {role, content}} compartida por todos los proveedores que soporta este proyecto.
 *
 * <p>Distinto de {@link com.korofin.backend.ai.entity.AiMessageRole}: ese enum es la
 * representación persistida en mayúscula ({@code USER}/{@code ASSISTANT}) usada en la tabla
 * {@code ai_messages}, mientras que {@link #role} acá es el string en minúscula que espera la API
 * del proveedor ({@code "system"}, {@code "user"}, {@code "assistant"}).
 *
 * @param role     uno de {@link #ROLE_SYSTEM}, {@link #ROLE_USER}, {@link #ROLE_ASSISTANT}
 * @param content  el texto del mensaje
 * @param imageUrl {@code null} para un mensaje de solo texto; en otro caso, una URL de imagen
 *                 ({@code https://} o un data URI {@code data:image/...;base64,...}) enviada junto
 *                 a {@code content} como un turno de visión (ver {@link #userWithImage}). Solo
 *                 {@code AiChatOrchestrator#completeVision} debería recibir mensajes con este
 *                 campo — el resto de las funcionalidades de IA de este proyecto solo construye
 *                 mensajes de texto plano a través de {@link #system}, {@link #user} o
 *                 {@link #assistant}.
 */
public record ChatMessage(String role, String content, String imageUrl) {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public static ChatMessage system(String content) {
        return new ChatMessage(ROLE_SYSTEM, content, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(ROLE_USER, content, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(ROLE_ASSISTANT, content, null);
    }

    /**
     * Construye un turno de usuario que lleva tanto texto como una imagen, para
     * {@code AiChatOrchestrator#completeVision}.
     *
     * @param text     la instrucción/pregunta que acompaña a la imagen (nunca {@code null})
     * @param imageUrl una URL {@code https://} o un data URI {@code data:image/...;base64,...} —
     *                 ambas formas son aceptadas tal cual por los campos {@code image_url.url}
     *                 OpenAI-compatibles, sin necesidad de subir la imagen a un hosting aparte
     * @return un mensaje de usuario con {@code imageUrl}, serializado por {@link AiChatClient}
     *         como el arreglo de partes de contenido de visión de OpenAI en vez de un string plano
     */
    public static ChatMessage userWithImage(String text, String imageUrl) {
        return new ChatMessage(ROLE_USER, text, imageUrl);
    }
}
