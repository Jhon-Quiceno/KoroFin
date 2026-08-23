enum ChatAuthor { user, assistant }

/// A single bubble in the "Asistente IA" chat.
class ChatMessage {
  const ChatMessage({required this.author, required this.text, required this.time});

  final ChatAuthor author;
  final String text;
  final DateTime time;
}
