import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/formatters.dart';
import '../../data/mock_data.dart';
import '../../models/chat_message.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';
import '../../widgets/nav/app_header.dart';

/// Screen 6 — Asistente IA: chat bubbles, empty state with suggestion
/// chips (two of which deep-link to Escaneo de recibos and Reportes), a
/// bottom composer and a small remaining-AI-usage indicator.
class AssistantScreen extends StatefulWidget {
  const AssistantScreen({super.key});

  @override
  State<AssistantScreen> createState() => _AssistantScreenState();
}

class _AssistantScreenState extends State<AssistantScreen> {
  late List<ChatMessage> _messages = List.of(MockData.chatHistory);
  final _inputController = TextEditingController();

  void _send([String? text]) {
    final content = (text ?? _inputController.text).trim();
    if (content.isEmpty) return;
    setState(() {
      _messages = [
        ..._messages,
        ChatMessage(author: ChatAuthor.user, text: content, time: DateTime.now()),
        ChatMessage(
          author: ChatAuthor.assistant,
          text: 'Estoy revisando tus movimientos para responderte con precisión. (Respuesta simulada)',
          time: DateTime.now(),
        ),
      ];
      _inputController.clear();
    });
  }

  void _onSuggestionTap(String suggestion) {
    if (suggestion == 'Escanear un recibo') {
      context.push('/receipt-scan');
      return;
    }
    if (suggestion == 'Ver mi reporte') {
      context.push('/reports');
      return;
    }
    _send(suggestion);
  }

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Column(
      children: [
        AppHeader(
          title: 'Asistente IA',
          subtitle: '12 consultas restantes hoy',
          onNotificationsTap: () => context.push('/notifications'),
          onProfileTap: () => context.push('/settings'),
          onSettingsTap: () => context.push('/settings'),
        ),
        Expanded(
          child: _messages.isEmpty
              ? _EmptyAssistant(onSuggestionTap: _onSuggestionTap)
              : ListView.builder(
                  padding: const EdgeInsets.all(AppSpacing.lg),
                  itemCount: _messages.length,
                  itemBuilder: (context, index) => _ChatBubble(message: _messages[index]),
                ),
        ),
        SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(AppSpacing.lg, 0, AppSpacing.lg, AppSpacing.lg),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _inputController,
                    decoration: const InputDecoration(hintText: 'Preguntale algo a KoroFin...'),
                    onSubmitted: _send,
                  ),
                ),
                const SizedBox(width: AppSpacing.sm),
                IconButton.filled(
                  onPressed: () => _send(),
                  style: IconButton.styleFrom(backgroundColor: koro.accent),
                  icon: const Icon(Icons.send_rounded, color: Colors.white, size: 18),
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
  const _EmptyAssistant({required this.onSuggestionTap});

  final ValueChanged<String> onSuggestionTap;

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
              decoration: BoxDecoration(color: koro.accent.withValues(alpha: 0.14), shape: BoxShape.circle),
              child: Icon(Icons.smart_toy_outlined, size: 30, color: koro.accent),
            ),
            const SizedBox(height: AppSpacing.lg),
            Text('Preguntale a tu asistente financiero', style: Theme.of(context).textTheme.titleLarge, textAlign: TextAlign.center),
            const SizedBox(height: AppSpacing.sm),
            Text(
              'Analizo tus movimientos y te ayudo a tomar mejores decisiones.',
              style: Theme.of(context).textTheme.bodyMedium,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: AppSpacing.xl),
            Wrap(
              spacing: AppSpacing.sm,
              runSpacing: AppSpacing.sm,
              alignment: WrapAlignment.center,
              children: [
                for (final suggestion in MockData.assistantSuggestions)
                  ActionChip(
                    label: Text(suggestion),
                    onPressed: () => onSuggestionTap(suggestion),
                  ),
              ],
            ),
          ],
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
    final bool isUser = message.author == ChatAuthor.user;
    return Align(
      alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        constraints: const BoxConstraints(maxWidth: 280),
        margin: const EdgeInsets.only(bottom: AppSpacing.md),
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md, vertical: AppSpacing.sm),
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
            Text(message.text, style: TextStyle(color: isUser ? Colors.white : koro.foreground, fontSize: 14)),
            const SizedBox(height: 4),
            Text(
              AppFormatters.time(message.time),
              style: TextStyle(color: isUser ? Colors.white70 : koro.mutedForeground, fontSize: 10),
            ),
          ],
        ),
      ),
    );
  }
}
