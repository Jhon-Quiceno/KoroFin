import '../../core/network/api_client.dart';
import '../../models/category.dart';

/// Acceso a `/api/categories` (CRUD de categorías del usuario actual).
class CategoryRepository {
  CategoryRepository(this._client);

  final ApiClient _client;

  Future<List<Category>> list({CategoryKind? kind}) async {
    final response = await _client.get(
      '/api/categories',
      query: kind == null ? null : <String, dynamic>{'type': kind.wire},
    );
    final List<dynamic> raw = response.data as List<dynamic>;
    return raw
        .map((dynamic e) => Category.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
  }

  Future<Category> create({
    required String name,
    required CategoryKind kind,
  }) async {
    final response = await _client.post(
      '/api/categories',
      body: <String, dynamic>{'name': name, 'type': kind.wire},
    );
    return Category.fromJson(response.data as Map<String, dynamic>);
  }

  Future<Category> update(
    int id, {
    required String name,
    required CategoryKind kind,
  }) async {
    final response = await _client.put(
      '/api/categories/$id',
      body: <String, dynamic>{'name': name, 'type': kind.wire},
    );
    return Category.fromJson(response.data as Map<String, dynamic>);
  }

  Future<void> delete(int id) async {
    await _client.delete('/api/categories/$id');
  }
}
