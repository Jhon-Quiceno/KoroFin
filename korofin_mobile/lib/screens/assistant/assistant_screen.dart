import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/network/api_exception.dart';
import '../../data/formatters.dart';
import '../../models/ai.dart';
import '../../state/ai/ai_controller.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';
import '../../widgets/nav/app_header.dart';

const List<String> _suggestions = [
  '¿Cuánto gasté en comida este mes?',
  '¿Cómo voy con mi presupuesto?',
  'Dame un consejo para ahorrar',
];

/// Pantalla 6 — Asistente IA: chat contra `/api/ai/chat`, con indicador de
/// cuota restante y aviso si no hay ningún proveedor de IA configurado.
class AssistantScreen extends ConsumerStatefulWidget {
  const AssistantScreen({super.key});

  @override
  ConsumerState<AssistantScreen> createState() => _AssistantScreenState();
}

class _AssistantScreenState extends ConsumerState<AssistantScreen> {
  final _input = TextEditingController();
  final _scroll = ScrollController();

  @override
  void dispose() {
    _input.dispose();
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _send([String? text]) async {
    final content = (text ?? _input.text).trim();
    if (content.isEmpty) return;
    _input.clear();
    final error =
        await ref.read(assistantProvider.notifier).send(content);
    if (!mounted) return;
    if (error != null) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error)));
    } else {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scroll.hasClients) {
          _scroll.animateTo(_scroll.position.maxScrollExtent,
              duration: const Duration(milliseconds: 250),
              curve: Curves.easeOut);
        }
      });
    }
    setState(() {}); // refresca el estado de "enviando"
  }

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    final AsyncValue<List<ChatMessage>> chat = ref.watch(assistantProvider);
    final bool providerOk = ref.watch(anyAiProviderConfiguredProvider);
    final String usageLabel = ref.watch(aiUsageProvider).maybeWhen(
          data: (u) => '${u.remaining} de ${u.limit} consultas restantes',
          orElse: () => 'Asistente financiero',
        );
    final bool sending = ref.read(assistantProvider.notifier).isSending;

    return Column(
      children: [
        AppHeader(
          title: 'Asistente IA',
          subtitle: usageLabel,
          onNotificationsTap: () => context.push('/notifications'),
          onProfileTap: () => context.push('/settings'),
          onSettingsTap: () => context.push('/settings'),
        ),
        if (!providerOk)
          Container(
            width: double.infinity,
            color: koro.warning.withValues(alpha: 0.14),
            padding: const EdgeInsets.all(AppSpacing.md),
            child: Text(
              'El asistente no está disponible: no hay ningún proveedor de IA configurado en el servidor.',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ),
        Expanded(
          child: chat.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (error, _) => Center(
              child: Text(error is ApiException
                  ? error.message
                  : 'No se pudo cargar el chat.'),
            ),
            data: (messages) => messages.isEmpty
                ? _EmptyAssistant(onTap: _send)
                : ListView.builder(
                    controller: _scroll,
                    padding: const EdgeInsets.all(AppSpacing.lg),
                    itemCount: messages.length + (sending ? 1 : 0),
                    itemBuilder: (context, index) {
                      if (index >= messages.length) {
                        return const _TypingBubble();
                      }
                      return _ChatBubble(message: messages[index]);
                    },
                  ),
          ),
        ),
        SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(
                AppSpacing.lg, 0, AppSpacing.lg, AppSpacing.lg),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _input,
                    enabled: providerOk && !sending,
                    decoration: const InputDecoration(
                        hintText: 'Preguntale algo a KoroFin...'),
                    onSubmitted: _send,
                  ),
                ),
                const SizedBox(width: AppSpacing.sm),
                IconButton.filled(
                  onPressed: (providerOk && !sending) ? () => _send() : null,
                  style: IconButton.styleFrom(backgroundColor: koro.accent),
                  icon: const Icon(Icons.send_rounded,
                      color: Colors.white, size: 18),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _EmptyAssistant extends StatelessWidget {
  const _EmptyAssistant({required this.onTap});
  final void Function(String) onTap;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xxl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 64,
              height: 64,
              decoration: BoxDecoration(
                  color: koro.accent.withValues(alpha: 0.14),
                  shape: BoxShape.circle),
              child: Icon(Icons.smart_toy_outlined,
                  size: 30, color: koro.accent),
            ),
            const SizedBox(height: AppSpacing.lg),
            Text('Preguntale a tu asistente financiero',
                style: Theme.of(context).textTheme.titleLarge,
                textAlign: TextAlign.center),
            const SizedBox(height: AppSpacing.sm),
            Text('Analizo tus movimientos y te ayudo a decidir mejor.',
                style: Theme.of(context).textTheme.bodyMedium,
                textAlign: TextAlign.center),
            const SizedBox(height: AppSpacing.xl),
            Wrap(
              spacing: AppSpacing.sm,
              runSpacing: AppSpacing.sm,
              alignment: WrapAlignment.center,
              children: [
                for (final s in _suggestions)
                  ActionChip(label: Text(s), onPressed: () => onTap(s)),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _TypingBubble extends StatelessWidget {
  const _TypingBubble();

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Align(
      alignment: Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(bottom: AppSpacing.md),
        padding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.md, vertical: AppSpacing.sm),
        decoration: BoxDecoration(
          color: koro.surfaceElevated,
          borderRadius: BorderRadius.circular(AppRadii.lg),
        ),
        child: const SizedBox(
          width: 28,
          height: 16,
          child: Center(
            child: SizedBox(
                width: 16,
                height: 16,
                child: CircularProgressIndicator(strokeWidth: 2)),
          ),
        ),
      ),
    );
  }
}

class _ChatBubble extends StatelessWidget {
  const _ChatBubble({required this.message});
  final ChatMessage message;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    final bool isUser = message.isUser;
    return Align(
      alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        constraints: const BoxConstraints(maxWidth: 300),
        margin: const EdgeInsets.only(bottom: AppSpacing.md),
        padding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.md, vertical: AppSpacing.sm),
        decoration: BoxDecoration(
          color: isUser ? koro.accent : koro.surfaceElevated,
          borderRadius: BorderRadius.only(
            topLeft: const Radius.circular(AppRadii.lg),
            topRight: const Radius.circular(AppRadii.lg),
            bottomLeft: Radius.circular(isUser ? AppRadii.lg : 2),
            bottomRight: Radius.circular(isUser ? 2 : AppRadii.lg),
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(message.content,
                style: TextStyle(
                    color: isUser ? Colors.white : koro.foreground,
                    fontSize: 14)),
            if (message.createdAt != null) ...[
              const SizedBox(height: 4),
              Text(
                AppFormatters.time(message.createdAt!),
                style: TextStyle(
                    color: isUser ? Colors.white70 : koro.mutedForeground,
                    fontSize: 10),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
