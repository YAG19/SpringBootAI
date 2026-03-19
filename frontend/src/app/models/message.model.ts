export interface UiMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  isStreaming?: boolean;
  provider?: string;
  model?: string;
  timestamp?: Date;
  error?: boolean;
}
