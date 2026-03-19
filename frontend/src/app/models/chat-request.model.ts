export type ProviderType = 'CLAUDE' | 'OPENAI' | 'LOCAL';

export interface MessageDto {
  role: 'user' | 'assistant';
  content: string;
}

export interface ChatRequest {
  message: string;
  provider?: ProviderType;
  model?: string;
  systemPrompt?: string;
  history?: MessageDto[];
}
