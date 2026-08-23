import 'package:flutter/material.dart';

import '../../data/formatters.dart';
import '../../models/imported_movement.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Screen 11 — Importar extractos: simulated drag & drop upload (PDF/CSV),
/// then a preview table with checkboxes to confirm which movements to
/// import.
class ImportStatementScreen extends StatefulWidget {
  const ImportStatementScreen({super.key});

  @override
  State<ImportStatementScreen> createState() => _ImportStatementScreenState();
}

class _ImportStatementScreenState extends State<ImportStatementScreen> {
  bool _uploaded = false;
  bool _confirmed = false;

  late final List<ImportedMovement> _movements = [
    ImportedMovement(description: 'Compra Falabella', amount: 215000, date: DateTime.now().subtract(const Duration(days: 1))),
    ImportedMovement(description: 'Transferencia recibida', amount: 500000, date: DateTime.now().subtract(const Duration(days: 2))),
    ImportedMovement(description: 'Pago EPM', amount: 98500, date: DateTime.now().subtract(const Duration(days: 3))),
    ImportedMovement(description: 'Rappi', amount: 42300, date: DateTime.now().subtract(const Duration(days: 4))),
  ];

  void _simulateUpload() {
    setState(() => _uploaded = true);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Importar extractos')),
      body: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: _confirmed
            ? _ConfirmationView(count: _movements.where((m) => m.selected).length)
            : !_uploaded
                ? _UploadView(onUpload: _simulateUpload)
                : _PreviewTable(
                    movements: _movements,
                    onToggle: (m, v) => setState(() => m.selected = v),
                    onConfirm: () => setState(() => _confirmed = true),
                  ),
      ),
    );
  }
}

class _UploadView extends StatelessWidget {
  const _UploadView({required this.onUpload});

  final VoidCallback onUpload;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          DottedBorderBox(onTap: onUpload),
          const SizedBox(height: AppSpacing.lg),
          Text('Arrastrá tu archivo acá', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: AppSpacing.xs),
          Text('Aceptamos PDF y CSV de tu banco', style: Theme.of(context).textTheme.bodyMedium),
          const SizedBox(height: AppSpacing.lg),
          ElevatedButton.icon(onPressed: onUpload, icon: const Icon(Icons.upload_file_outlined), label: const Text('Seleccionar archivo')),
        ],
      ),
    );
  }
}

class DottedBorderBox extends StatelessWidget {
  const DottedBorderBox({super.key, required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(AppRadii.lg),
      child: Container(
        width: 220,
        height: 140,
        decoration: BoxDecoration(
          color: koro.surfaceElevated,
          borderRadius: BorderRadius.circular(AppRadii.lg),
          border: Border.all(color: koro.border, width: 1.5),
        ),
        child: Icon(Icons.description_outlined, size: 40, color: koro.mutedForeground),
      ),
    );
  }
}

class _PreviewTable extends StatelessWidget {
  const _PreviewTable({required this.movements, required this.onToggle, required this.onConfirm});

  final List<ImportedMovement> movements;
  final void Function(ImportedMovement, bool) onToggle;
  final VoidCallback onConfirm;

  @override
  Widget build(BuildContext context) {
    final selectedCount = movements.where((m) => m.selected).length;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Detectamos ${movements.length} movimientos', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: AppSpacing.sm),
        Expanded(
          child: ListView.separated(
            itemCount: movements.length,
            separatorBuilder: (_, _) => const Divider(height: 1),
            itemBuilder: (context, index) {
              final movement = movements[index];
              return CheckboxListTile(
                contentPadding: EdgeInsets.zero,
                value: movement.selected,
                onChanged: (v) => onToggle(movement, v ?? false),
                title: Text(movement.description),
                subtitle: Text(AppFormatters.shortDate(movement.date)),
                secondary: Text(AppFormatters.currency(movement.amount), style: Theme.of(context).textTheme.titleMedium),
              );
            },
          ),
        ),
        const SizedBox(height: AppSpacing.md),
        ElevatedButton(
          onPressed: selectedCount == 0 ? null : onConfirm,
          child: Text('Confirmar importación ($selectedCount)'),
        ),
      ],
    );
  }
}

class _ConfirmationView extends StatelessWidget {
  const _ConfirmationView({required this.count});

  final int count;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.check_circle_outline, size: 56, color: koro.success),
          const SizedBox(height: AppSpacing.lg),
          Text('$count movimientos importados', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: AppSpacing.sm),
          Text('Ya están disponibles en Movimientos.', style: Theme.of(context).textTheme.bodyMedium),
        ],
      ),
    );
  }
}
