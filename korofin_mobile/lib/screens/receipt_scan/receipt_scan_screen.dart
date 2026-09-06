import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/network/api_exception.dart';
import '../../data/category_visuals.dart';
import '../../models/ai.dart';
import '../../models/category.dart';
import '../../models/movement.dart';
import '../../state/ai/ai_controller.dart';
import '../../state/movements/movements_controller.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';
import '../categories/category_picker_sheet.dart';

/// Pantalla 13 — Escaneo de recibos: captura con la cámara nativa, envía la
/// foto a `POST /api/receipts/scan` y muestra un formulario editable antes de
/// crear el movimiento real.
class ReceiptScanScreen extends ConsumerStatefulWidget {
  const ReceiptScanScreen({super.key});

  @override
  ConsumerState<ReceiptScanScreen> createState() => _ReceiptScanScreenState();
}

enum _Stage { capture, loading, result, notReceipt }

class _ReceiptScanScreenState extends ConsumerState<ReceiptScanScreen> {
  final _picker = ImagePicker();
  _Stage _stage = _Stage.capture;
  String? _error;

  final _amount = TextEditingController();
  final _description = TextEditingController();
  int? _categoryId;
  String? _categoryName;
  MovementType _type = MovementType.expense;
  bool _saving = false;

  @override
  void dispose() {
    _amount.dispose();
    _description.dispose();
    super.dispose();
  }

  Future<void> _capture(ImageSource source) async {
    setState(() => _error = null);
    final XFile? file = await _picker.pickImage(
      source: source,
      maxWidth: 1600,
      imageQuality: 70,
    );
    if (file == null) return;
    setState(() => _stage = _Stage.loading);
    try {
      final bytes = await file.readAsBytes();
      final String dataUri = 'data:image/jpeg;base64,${base64Encode(bytes)}';
      final ReceiptExtraction result =
          await ref.read(aiRepositoryProvider).scanReceipt(dataUri);
      if (!mounted) return;
      if (!result.isReceipt) {
        setState(() => _stage = _Stage.notReceipt);
        return;
      }
      setState(() {
        _amount.text = result.amount?.toStringAsFixed(0) ?? '';
        _description.text = result.description ?? '';
        _categoryId = result.categoryId;
        _categoryName = result.categoryName;
        _type =
            result.isIncome ? MovementType.income : MovementType.expense;
        _stage = _Stage.result;
      });
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _stage = _Stage.capture;
          _error = e.message;
        });
      }
    }
  }

  Future<void> _pickCategory() async {
    final Category? picked = await showCategoryPicker(
      context,
      kind: _type == MovementType.expense
          ? CategoryKind.expense
          : CategoryKind.income,
    );
    if (picked != null) {
      setState(() {
        _categoryId = picked.id;
        _categoryName = picked.name;
      });
    }
  }

  Future<void> _save() async {
    final double? amount = double.tryParse(
        _amount.text.replaceAll('.', '').replaceAll(',', ''));
    if (amount == null || amount <= 0) {
      setState(() => _error = 'Ingresá un monto válido.');
      return;
    }
    setState(() => _saving = true);
    try {
      await ref.read(movementsProvider(_type).notifier).add(MovementDraft(
            amount: amount,
            date: DateTime.now(),
            description:
                _description.text.trim().isEmpty ? null : _description.text.trim(),
            categoryId: _categoryId,
            paymentMethod:
                _type == MovementType.expense ? PaymentMethod.cash : null,
          ));
      if (mounted) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Movimiento creado desde el recibo')),
        );
      }
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Escanear recibo')),
      body: switch (_stage) {
        _Stage.capture => _CaptureView(
            error: _error,
            onCamera: () => _capture(ImageSource.camera),
            onGallery: () => _capture(ImageSource.gallery),
          ),
        _Stage.loading => const _LoadingView(),
        _Stage.notReceipt => _NotReceiptView(
            onRetry: () => setState(() => _stage = _Stage.capture),
          ),
        _Stage.result => _buildResult(context),
      },
    );
  }

  Widget _buildResult(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(AppSpacing.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Datos detectados',
              style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: AppSpacing.xs),
          Text('Revisá y ajustá antes de guardar.',
              style: Theme.of(context).textTheme.bodyMedium),
          const SizedBox(height: AppSpacing.lg),
          SegmentedButton<MovementType>(
            segments: const [
              ButtonSegment(value: MovementType.expense, label: Text('Gasto')),
              ButtonSegment(value: MovementType.income, label: Text('Ingreso')),
            ],
            selected: {_type},
            onSelectionChanged: (s) => setState(() {
              _type = s.first;
              _categoryId = null;
              _categoryName = null;
            }),
          ),
          const SizedBox(height: AppSpacing.md),
          TextField(
            controller: _amount,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'Monto'),
          ),
          const SizedBox(height: AppSpacing.md),
          TextField(
            controller: _description,
            decoration: const InputDecoration(labelText: 'Descripción'),
          ),
          const SizedBox(height: AppSpacing.md),
          InkWell(
            borderRadius: BorderRadius.circular(AppRadii.md),
            onTap: _pickCategory,
            child: InputDecorator(
              decoration:
                  const InputDecoration(labelText: 'Categoría sugerida'),
              child: Row(
                children: [
                  Icon(CategoryVisuals.iconForName(_categoryName),
                      size: 18,
                      color: CategoryVisuals.colorForName(_categoryName)),
                  const SizedBox(width: AppSpacing.sm),
                  Text(_categoryName ?? 'Sin categoría'),
                  const Spacer(),
                  const Icon(Icons.chevron_right_rounded, size: 18),
                ],
              ),
            ),
          ),
          if (_error != null) ...[
            const SizedBox(height: AppSpacing.md),
            Text(_error!,
                style:
                    TextStyle(color: Theme.of(context).colorScheme.error)),
          ],
          const Spacer(),
          ElevatedButton(
            onPressed: _saving ? null : _save,
            child: _saving
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2.5))
                : const Text('Guardar movimiento'),
          ),
        ],
      ),
    );
  }
}

class _CaptureView extends StatelessWidget {
  const _CaptureView({
    required this.onCamera,
    required this.onGallery,
    this.error,
  });

  final VoidCallback onCamera;
  final VoidCallback onGallery;
  final String? error;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Padding(
      padding: const EdgeInsets.all(AppSpacing.lg),
      child: Column(
        children: [
          Expanded(
            child: Container(
              width: double.infinity,
              decoration: BoxDecoration(
                color: const Color(0xFF0F172A),
                borderRadius: BorderRadius.circular(AppRadii.xl),
                border: Border.all(color: koro.border),
              ),
              child: const Center(
                child: Icon(Icons.receipt_long_outlined,
                    size: 64, color: Color(0xFF475569)),
              ),
            ),
          ),
          if (error != null) ...[
            const SizedBox(height: AppSpacing.md),
            Text(error!,
                style:
                    TextStyle(color: Theme.of(context).colorScheme.error)),
          ],
          const SizedBox(height: AppSpacing.lg),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: onCamera,
                  icon: const Icon(Icons.photo_camera_outlined),
                  label: const Text('Tomar foto'),
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: onGallery,
                  icon: const Icon(Icons.image_outlined),
                  label: const Text('Elegir imagen'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _LoadingView extends StatelessWidget {
  const _LoadingView();

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          CircularProgressIndicator(color: koro.accent),
          const SizedBox(height: AppSpacing.lg),
          Text('Extrayendo datos del recibo...',
              style: Theme.of(context).textTheme.titleMedium),
        ],
      ),
    );
  }
}

class _NotReceiptView extends StatelessWidget {
  const _NotReceiptView({required this.onRetry});
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.image_not_supported_outlined, size: 48),
            const SizedBox(height: AppSpacing.md),
            Text('Eso no parece un recibo',
                style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: AppSpacing.sm),
            const Text('Probá con una foto más clara del comprobante.',
                textAlign: TextAlign.center),
            const SizedBox(height: AppSpacing.lg),
            FilledButton.tonal(
                onPressed: onRetry, child: const Text('Volver a intentar')),
          ],
        ),
      ),
    );
  }
}
