/// Envoltura genérica de las respuestas paginadas de Spring Data (`Page<T>`).
///
/// El backend devuelve `{ content, number, totalElements, totalPages, last, ... }`
/// en los listados con paginación (gastos, ingresos, movimientos de tarjeta,
/// historial de chat). `number` es la página actual en base 0.
class PageResponse<T> {
  const PageResponse({
    required this.content,
    required this.page,
    required this.totalElements,
    required this.totalPages,
    required this.isLast,
  });

  final List<T> content;
  final int page;
  final int totalElements;
  final int totalPages;
  final bool isLast;

  bool get hasMore => !isLast;

  factory PageResponse.fromJson(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic> item) itemFromJson,
  ) {
    final List<dynamic> rawContent =
        (json['content'] as List<dynamic>?) ?? const <dynamic>[];
    return PageResponse<T>(
      content: rawContent
          .map((dynamic e) => itemFromJson(e as Map<String, dynamic>))
          .toList(growable: false),
      page: (json['number'] as num?)?.toInt() ?? 0,
      totalElements:
          (json['totalElements'] as num?)?.toInt() ?? rawContent.length,
      totalPages: (json['totalPages'] as num?)?.toInt() ?? 1,
      isLast: json['last'] as bool? ?? true,
    );
  }
}
