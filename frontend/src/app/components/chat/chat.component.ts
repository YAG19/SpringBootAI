import {
  Component,
  ElementRef,
  ViewChild,
  AfterViewChecked,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TextFieldModule } from '@angular/cdk/text-field';

import { ChatService } from '../../services/chat.service';
import { ChatSettings, SettingsPanelComponent } from '../settings-panel/settings-panel.component';
import { MessageBubbleComponent } from '../message-bubble/message-bubble.component';
import { UiMessage } from '../../models/message.model';
import { MessageDto } from '../../models/chat-request.model';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatFormFieldModule,
    MatSidenavModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    TextFieldModule,
    SettingsPanelComponent,
    MessageBubbleComponent,
  ],
  templateUrl: './chat.component.html',
  styleUrls: ['./chat.component.scss'],
})
export class ChatComponent implements AfterViewChecked {
  @ViewChild('messagesEnd') private messagesEnd!: ElementRef<HTMLDivElement>;

  messages: UiMessage[] = [];
  inputText = '';
  isLoading = false;
  settingsPanelOpen = false;
  private shouldScroll = false;

  settings: ChatSettings = {
    provider: 'LOCAL',
    model: '',
    systemPrompt: '',
    streaming: true,
  };

  constructor(private chatService: ChatService) {}

  ngAfterViewChecked(): void {
    if (this.shouldScroll) {
      this.scrollToBottom();
      this.shouldScroll = false;
    }
  }

  onSettingsChange(s: ChatSettings): void {
    this.settings = s;
  }

  onClearConversation(): void {
    this.messages = [];
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  send(): void {
    const text = this.inputText.trim();
    if (!text || this.isLoading) return;

    // Build conversation history from existing messages
    const history: MessageDto[] = this.messages
      .filter(m => !m.error)
      .map(m => ({ role: m.role, content: m.content }));

    // Add user message to UI
    const userMsg: UiMessage = {
      id: crypto.randomUUID(),
      role: 'user',
      content: text,
      timestamp: new Date(),
    };
    this.messages.push(userMsg);
    this.inputText = '';
    this.isLoading = true;
    this.shouldScroll = true;

    const request = {
      message: text,
      provider: this.settings.provider,
      model: this.settings.model || undefined,
      systemPrompt: this.settings.systemPrompt || undefined,
      history: history.length ? history : undefined,
    };

    if (this.settings.streaming) {
      this.sendStreaming(request);
    } else {
      this.sendFull(request);
    }
  }

  private sendStreaming(request: any): void {
    // Pre-create the assistant bubble
    const assistantMsg: UiMessage = {
      id: crypto.randomUUID(),
      role: 'assistant',
      content: '',
      isStreaming: true,
    };
    this.messages.push(assistantMsg);
    this.shouldScroll = true;

    this.chatService.streamChat(request).subscribe({
      next: chunk => {
        assistantMsg.content += chunk;
        this.shouldScroll = true;
      },
      error: err => {
        assistantMsg.isStreaming = false;
        assistantMsg.content = assistantMsg.content || `Error: ${err.message}`;
        assistantMsg.error = !assistantMsg.content || assistantMsg.content.startsWith('Error:');
        this.isLoading = false;
        this.shouldScroll = true;
      },
      complete: () => {
        assistantMsg.isStreaming = false;
        assistantMsg.provider = this.settings.provider;
        assistantMsg.model = this.settings.model || this.providerDefaultModel();
        assistantMsg.timestamp = new Date();
        this.isLoading = false;
        this.shouldScroll = true;
      },
    });
  }

  private sendFull(request: any): void {
    this.chatService.chat(request).subscribe({
      next: response => {
        this.messages.push({
          id: crypto.randomUUID(),
          role: 'assistant',
          content: response.content,
          provider: response.provider,
          model: response.model,
          timestamp: new Date(response.timestamp),
        });
        this.isLoading = false;
        this.shouldScroll = true;
      },
      error: err => {
        this.messages.push({
          id: crypto.randomUUID(),
          role: 'assistant',
          content: `Error: ${err.message ?? 'Unknown error'}`,
          error: true,
          timestamp: new Date(),
        });
        this.isLoading = false;
        this.shouldScroll = true;
      },
    });
  }

  private providerDefaultModel(): string {
    const defaults: Record<string, string> = {
      CLAUDE: 'claude-sonnet-4-6',
      OPENAI: 'gpt-4o',
      LOCAL: 'local-model',
    };
    return defaults[this.settings.provider] ?? '';
  }

  trackById(_: number, msg: UiMessage): string {
    return msg.id;
  }

  private scrollToBottom(): void {
    try {
      this.messagesEnd?.nativeElement.scrollIntoView({ behavior: 'smooth' });
    } catch {}
  }
}
