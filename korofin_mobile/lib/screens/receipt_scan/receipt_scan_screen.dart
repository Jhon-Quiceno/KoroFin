import 'package:flutter/material.dart';

import '../../data/mock_data.dart';
import '../../models/category.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

enum _ScanStage { capture, loading, result }

/// Screen 13 — Escaneo de recibos: simulated camera capture, a loading
/// state ("Extrayendo datos...") and an editable result before saving as
/// a new expense.
class ReceiptScanScreen extends StatefulWidget {
  const ReceiptScanScreen({super.key});

  @override
  State<ReceiptScanScreen> createState() => _ReceiptScanScreenState();
}

class _ReceiptScanScreenState extends State<ReceiptScanScreen> {
  _ScanStage _stage = _ScanStage.capture;
  final _amountController = TextEditingController(text: '68500');
  final _merchantController = TextEditingController(text: 'Panadería San José');
  final AppCategory _category = MockData.categoryById('food');

  Future<void> _simulateCapture() async {
    setState(() => _stage = _ScanStage.loading);
    await Future.delayed(const Duration(milliseconds: 1400));
    if (mounted) setState(() => _stage = _ScanStage.result);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Escanear recibo')),
      body: switch (_stage) {
        _ScanStage.capture => _CaptureView(onCapture: _simulateCapture),
        _ScanStage.loading => const _LoadingView(),
        _ScanStage.result => _ResultView(
            amountController: _amountController,
            merchantController: _merchantController,
            category: _category,
            // El escaneo real de recibos y su selección de categoría llegan en
            // la Fase 11 (cámara nativa + POST /api/receipts/scan).
            onPickCategory: () => ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('Disponible en la Fase 11')),
            ),
          ),
      },
    );
  }
}

class _CaptureView extends StatelessWidget {
  const _CaptureView({required this.onCapture});

  final VoidCallback onCapture;

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
                child: Icon(Icons.receipt_long_outlined, size: 64, color: Color(0xFF475569)),
              ),
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(onPressed: onCapture, icon: const Icon(Icons.photo_camera_outlined), label: const Text('Tomar foto')),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: ElevatedButton.icon(onPressed: onCapture, icon: const Icon(Icons.upload_outlined), label: const Text('Subir imagen')),
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
          Text('Extrayendo datos...', style: Theme.of(context).textTheme.titleMedium),
        ],
      ),
    );
  }
}

class _ResultView extends StatelessWidget {
  const _ResultView({
    required this.amountController,
    required this.merchantController,
    required this.category,
    required this.onPickCategory,
  });

  final TextEditingController amountController;
  final TextEditingController merchantController;
  final AppCategory category;
  final VoidCallback onPickCategory;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(AppSpacing.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Datos detectados', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: AppSpacing.xs),
          Text('Revisá y ajustá antes de guardar como gasto.', style: Theme.of(context).textTheme.bodyMedium),
          const SizedBox(height: AppSpacing.lg),
          TextField(controller: amountController, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Monto')),
          const SizedBox(height: AppSpacing.md),
          TextField(controller: merchantController, decoration: const InputDecoration(labelText: 'Comercio')),
          const SizedBox(height: AppSpacing.md),
          InputDecorator(
            decoration: const InputDecoration(labelText: 'Fecha'),
            child: Text('${DateTime.now().day}/${DateTime.now().month}/${DateTime.now().year}'),
          ),
          const SizedBox(height: AppSpacing.md),
          InkWell(
            onTap: onPickCategory,
            child: InputDecorator(
              decoration: const InputDecoration(labelText: 'Categoría sugerida'),
              child: Row(
                children: [
                  Icon(category.icon, size: 18, color: category.color),
                  const SizedBox(width: AppSpacing.sm),
                  Text(category.name),
                  const Spacer(),
                  const Icon(Icons.chevron_right_rounded, size: 18),
                ],
              ),
            ),
          ),
          const Spacer(),
          ElevatedButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Guardar como gasto'),
          ),
        ],
      ),
    );
  }
}
