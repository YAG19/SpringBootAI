import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ChatRequest } from '../models/chat-request.model';
import { ChatResponse } from '../models/chat-response.model';

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly baseUrl = '/api/chat';

  constructor(private http: HttpClient) {}

  /**
   * Non-streaming chat — returns the complete response as a single JSON object.
   */
  chat(request: ChatRequest): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(this.baseUrl, request);
  }

  /**
   * Streaming chat via SSE using the Fetch API + ReadableStream.
   *
   * Emits one string per token chunk received from the server.
   * Completes when the server sends the "done" event with data "[DONE]".
   */
  streamChat(request: ChatRequest): Observable<string> {
    return new Observable<string>(observer => {
      const controller = new AbortController();

      fetch(`${this.baseUrl}/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
        },
        body: JSON.stringify(request),
        signal: controller.signal,
      })
        .then(response => {
          if (!response.ok) {
            observer.error(new Error(`HTTP ${response.status}: ${response.statusText}`));
            return;
          }
          const reader = response.body!.getReader();
          const decoder = new TextDecoder();
          let buffer = '';

          const pump = (): Promise<void> =>
            reader.read().then(({ done, value }) => {
              if (done) {
                observer.complete();
                return;
              }

              buffer += decoder.decode(value, { stream: true });

              // Process complete SSE lines
              const lines = buffer.split('\n');
              // Keep the last (potentially incomplete) line in the buffer
              buffer = lines.pop() ?? '';

              let eventType = '';
              for (const line of lines) {
                if (line.startsWith('event:')) {
                  eventType = line.slice(6).trim();
                } else if (line.startsWith('data:')) {
                  const data = line.slice(5).trim();
                  if (eventType === 'done' || data === '[DONE]') {
                    observer.complete();
                    return;
                  }
                  if (data) {
                    observer.next(data);
                  }
                }
              }

              return pump();
            });

          pump().catch(err => {
            if (err.name !== 'AbortError') {
              observer.error(err);
            }
          });
        })
        .catch(err => {
          if (err.name !== 'AbortError') {
            observer.error(err);
          }
        });

      // Teardown: abort the fetch when the Observable is unsubscribed
      return () => controller.abort();
    });
  }
}
