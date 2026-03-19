import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { ProviderType } from '../../models/chat-request.model';

export interface ChatSettings {
  provider: ProviderType;
  model: string;
  systemPrompt: string;
  streaming: boolean;
}

@Component({
  selector: 'app-settings-panel',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatSlideToggleModule,
    MatButtonModule,
    MatIconModule,
    MatDividerModule,
  ],
  templateUrl: './settings-panel.component.html',
  styleUrls: ['./settings-panel.component.scss'],
})
export class SettingsPanelComponent implements OnChanges {
  @Input() settings!: ChatSettings;
  @Output() settingsChange = new EventEmitter<ChatSettings>();
  @Output() clearConversation = new EventEmitter<void>();

  readonly providers: ProviderType[] = ['LOCAL', 'CLAUDE', 'OPENAI'];

  form!: FormGroup;

  constructor(private fb: FormBuilder) {
    this.form = this.fb.group({
      provider: ['LOCAL'],
      model: [''],
      systemPrompt: [''],
      streaming: [true],
    });

    this.form.valueChanges.subscribe(value => {
      this.settingsChange.emit({
        provider: value.provider,
        model: value.model?.trim() || '',
        systemPrompt: value.systemPrompt?.trim() || '',
        streaming: value.streaming,
      });
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['settings'] && this.settings) {
      this.form.patchValue(this.settings, { emitEvent: false });
    }
  }

  onClear(): void {
    this.clearConversation.emit();
  }
}
