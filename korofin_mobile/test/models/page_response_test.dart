import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/models/page_response.dart';

void main() {
  test('parsea un Page<T> de Spring y mapea el contenido', () {
    final json = <String, dynamic>{
      'content': <dynamic>[
        <String, dynamic>{'id': 1, 'title': 'Café'},
        <String, dynamic>{'id': 2, 'title': 'Almuerzo'},
      ],
      'number': 0,
      'totalElements': 5,
      'totalPages': 3,
      'last': false,
    };

    final page = PageResponse<String>.fromJson(
      json,
      (item) => item['title'] as String,
    );

    expect(page.content, <String>['Café', 'Almuerzo']);
    expect(page.page, 0);
    expect(page.totalElements, 5);
    expect(page.totalPages, 3);
    expect(page.isLast, isFalse);
    expect(page.hasMore, isTrue);
  });

  test('tolera un JSON incompleto asumiendo página única', () {
    final page = PageResponse<int>.fromJson(
      <String, dynamic>{'content': <dynamic>[]},
      (item) => item['id'] as int,
    );

    expect(page.content, isEmpty);
    expect(page.page, 0);
    expect(page.isLast, isTrue);
    expect(page.hasMore, isFalse);
  });
}
