import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/api_client.dart';
import 'package:korofin_mobile/core/network/api_exception.dart';
import 'package:korofin_mobile/data/repositories/category_repository.dart';
import 'package:korofin_mobile/models/category.dart';

import '../support/capturing_adapter.dart';

CategoryRepository _repoWith(CapturingAdapter adapter) {
  final Dio dio = Dio()..httpClientAdapter = adapter;
  return CategoryRepository(ApiClient(readAccessToken: () => 'tok', dio: dio));
}

void main() {
  test('list sin filtro pega a /api/categories y parsea la lista', () async {
    final adapter = CapturingAdapter(body: <dynamic>[
      <String, dynamic>{'id': 1, 'name': 'Comida', 'type': 'EXPENSE'},
      <String, dynamic>{'id': 2, 'name': 'Salario', 'type': 'INCOME'},
    ]);
    final repo = _repoWith(adapter);

    final list = await repo.list();

    expect(adapter.lastRequest.path, '/api/categories');
    expect(adapter.lastRequest.queryParameters, isEmpty);
    expect(list, hasLength(2));
    expect(list[1].kind, CategoryKind.income);
  });

  test('list con kind manda ?type=EXPENSE', () async {
    final adapter = CapturingAdapter(body: <dynamic>[]);
    final repo = _repoWith(adapter);

    await repo.list(kind: CategoryKind.expense);

    expect(adapter.lastRequest.queryParameters, <String, dynamic>{
      'type': 'EXPENSE',
    });
  });

  test('create hace POST con name/type y devuelve la categoría creada',
      () async {
    final adapter = CapturingAdapter(
      status: 201,
      body: <String, dynamic>{'id': 9, 'name': 'Mascotas', 'type': 'EXPENSE'},
    );
    final repo = _repoWith(adapter);

    final created =
        await repo.create(name: 'Mascotas', kind: CategoryKind.expense);

    expect(adapter.lastRequest.method, 'POST');
    expect((adapter.lastRequest.data as Map)['name'], 'Mascotas');
    expect((adapter.lastRequest.data as Map)['type'], 'EXPENSE');
    expect(created.id, 9);
  });

  test('update hace PUT a /api/categories/{id}', () async {
    final adapter = CapturingAdapter(
      body: <String, dynamic>{'id': 9, 'name': 'Mascota', 'type': 'EXPENSE'},
    );
    final repo = _repoWith(adapter);

    await repo.update(9, name: 'Mascota', kind: CategoryKind.expense);

    expect(adapter.lastRequest.method, 'PUT');
    expect(adapter.lastRequest.path, '/api/categories/9');
  });

  test('delete hace DELETE a /api/categories/{id}', () async {
    final adapter = CapturingAdapter(status: 204);
    final repo = _repoWith(adapter);

    await repo.delete(9);

    expect(adapter.lastRequest.method, 'DELETE');
    expect(adapter.lastRequest.path, '/api/categories/9');
  });

  test('un 409 por nombre duplicado se propaga como ApiException', () async {
    final adapter = CapturingAdapter(
      status: 409,
      body: <String, dynamic>{'message': 'Ya existe una categoría con ese nombre'},
    );
    final repo = _repoWith(adapter);

    expect(
      () => repo.create(name: 'Comida', kind: CategoryKind.expense),
      throwsA(isA<ApiException>()
          .having((e) => e.statusCode, 'statusCode', 409)),
    );
  });
}
