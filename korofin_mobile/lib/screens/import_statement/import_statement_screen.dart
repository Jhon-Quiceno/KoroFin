import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import '../../data/formatters.dart';
import '../../models/movement.dart';
import '../../models/statement.dart';
import '../../state/movements/movements_controller.dart';
import '../../state/statement/statement_controller.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Pantalla 11 — Importar extractos: elegí un archivo (PDF/CSV/XLSX), revisá
/// las filas detectadas (con los duplicados marcados) y confirmá cuáles
/// importar contra `/api/statement-imports/*`.
class ImportStatementScreen extends ConsumerStatefulWidget {
  const ImportStatementScreen({super.key});

  @override
  ConsumerState<ImportStatementScreen> createState() =>
      _ImportStatementScreenState();
}

enum _Stage { pick, loading, preview, done }

class _ImportStatementScreenState
    extends ConsumerState<ImportStatementScreen> {
  _Stage _stage = _Stage.pick;
  String? _error;
  int _createdCount = 0;

  Uint8List? _bytes;
  String? _filename;
  final _password = TextEditingController();
  bool _needsPassword = false;

  StatementPreview? _preview;

  @override
  void dispose() {
    _password.dispose();
    super.dispose();
  }

  Future<void> _pickFile() async {
    setState(() => _error = null);
    final FilePickerResult? result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: const ['pdf', 'csv', 'xlsx'],
      withData: true,
    );
    final PlatformFile? file = result?.files.singleOrNull;
    if (file == null || file.bytes == null) return;
    _bytes = file.bytes;
    _filename = file.name;
    await _runPreview();
  }

  Future<void> _runPreview() async {
    if (_bytes == null || _filename == null) return;
    setState(() {
      _stage = _Stage.loading;
      _error = null;
    });
    try {
      final StatementPreview preview =
          await ref.read(statementRepositoryProvider).preview(
                bytes: _bytes!,
                filename: _filename!,
                password: _password.text,
              );
      setState(() {
        _preview = preview;
        _needsPassword = false;
        _stage = _Stage.preview;
      });
    } on ApiException catch (e) {
      setState(() {
        // 422 con "contraseña" → pedimos la contraseña y dejamos reintentar.
        _needsPassword =
            e.statusCode == 422 && e.message.toLowerCase().contains('contrase');
        _error = e.message;
        _stage = _Stage.pick;
      });
    }
  }

  Future<void> _confirm() async {
    final List<StatementRow> selected =
        _preview!.rows.where((r) => r.selected).toList(growable: false);
    if (selected.isEmpty) {
      setState(() => _error = 'Elegí al menos una fila.');
      return;
    }
    setState(() {
      _stage = _Stage.loading;
      _error = null;
    });
    try {
      final int created =
          await ref.read(statementRepositoryProvider).confirm(selected);
      // Refrescar las listas de movimientos ya cargadas.
      ref.invalidate(movementsProvider(MovementType.expense));
      ref.invalidate(movementsProvider(MovementType.income));
      setState(() {
        _createdCount = created;
        _stage = _Stage.done;
      });
    } on ApiException catch (e) {
      setState(() {
        _error = e.message;
        _stage = _Stage.preview;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Importar extractos')),
      body: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: switch (_stage) {
          _Stage.pick => _PickView(
              error: _error,
              needsPassword: _needsPassword,
              passwordController: _password,
              onPick: _pickFile,
              onRetry: _bytes != null ? _runPreview : null,
            ),
          _Stage.loading => const Center(child: CircularProgressIndicator()),
          _Stage.preview => _PreviewView(
              preview: _preview!,
              error: _error,
              onToggle: (row, v) => setState(() => row.selected = v),
              onConfirm: _confirm,
            ),
          _Stage.done => _DoneView(count: _createdCount),
        },
      ),
    );
  }
}

class _PickView extends StatelessWidget {
  const _PickView({
    required this.onPick,
    required this.passwordController,
    required this.needsPassword,
    this.error,
    this.onRetry,
  });

  final VoidCallback onPick;
  final TextEditingController passwordController;
  final bool needsPassword;
  final String? error;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Container(
          padding: const EdgeInsets.all(AppSpacing.xxl),
          decoration: BoxDecoration(
            border: Border.all(color: koro.border),
            borderRadius: BorderRadius.circular(AppRadii.lg),
          ),
          child: Column(
            children: [
              Icon(Icons.upload_file_outlined, size: 48, color: koro.accent),
              const SizedBox(height: AppSpacing.md),
              const Text('PDF, CSV o XLSX de tu banco',
                  textAlign: TextAlign.center),
              const SizedBox(height: AppSpacing.md),
              ElevatedButton(
                  onPressed: onPick, child: const Text('Elegir archivo')),
            ],
          ),
        ),
        if (needsPassword) ...[
          const SizedBox(height: AppSpacing.lg),
          TextField(
            controller: passwordController,
            obscureText: true,
            decoration: const InputDecoration(
                labelText: 'Contraseña del PDF'),
          ),
          const SizedBox(height: AppSpacing.sm),
          FilledButton.tonal(
              onPressed: onRetry, child: const Text('Reintentar')),
        ],
        if (error != null) ...[
          const SizedBox(height: AppSpacing.md),
          Text(error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error)),
        ],
      ],
    );
  }
}

class _PreviewView extends StatelessWidget {
  const _PreviewView({
    required this.preview,
    required this.onToggle,
    required this.onConfirm,
    this.error,
  });

  final StatementPreview preview;
  final void Function(StatementRow row, bool selected) onToggle;
  final VoidCallback onConfirm;
  final String? error;

  @override
  Widget build(BuildContext context) {
    final int selectedCount = preview.rows.where((r) => r.selected).length;
    return Column(
      children: [
        Text(
          '${preview.totalRows} movimientos · ${preview.duplicateRows} posibles duplicados',
          style: Theme.of(context).textTheme.bodyMedium,
        ),
        const SizedBox(height: AppSpacing.sm),
        Expanded(
          child: ListView.builder(
            itemCount: preview.rows.length,
            itemBuilder: (context, index) {
              final StatementRow r = preview.rows[index];
              return CheckboxListTile(
                contentPadding: EdgeInsets.zero,
                value: r.selected,
                onChanged: (v) => onToggle(r, v ?? false),
                title: Text(
                  r.description.isEmpty ? 'Sin descripción' : r.description,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                subtitle: Text(
                  '${AppFormatters.shortDate(r.date)} · '
                  '${r.type == MovementType.income ? '+' : '-'}'
                  '${AppFormatters.currency(r.amount)}'
                  '${r.isDuplicate ? ' · posible duplicado' : ''}'
                  '${r.suggestedCategoryName != null ? ' · ${r.suggestedCategoryName}' : ''}',
                ),
              );
            },
          ),
        ),
        if (error != null) ...[
          const SizedBox(height: AppSpacing.sm),
          Text(error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error)),
        ],
        const SizedBox(height: AppSpacing.sm),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            onPressed: selectedCount == 0 ? null : onConfirm,
            child: Text('Importar $selectedCount movimiento'
                '${selectedCount == 1 ? '' : 's'}'),
          ),
        ),
      ],
    );
  }
}

class _DoneView extends StatelessWidget {
  const _DoneView({required this.count});
  final int count;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.check_circle_outline, size: 56),
          const SizedBox(height: AppSpacing.md),
          Text('$count movimiento${count == 1 ? '' : 's'} importado'
              '${count == 1 ? '' : 's'}',
              style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: AppSpacing.lg),
          FilledButton.tonal(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Listo'),
          ),
        ],
      ),
    );
  }
}
