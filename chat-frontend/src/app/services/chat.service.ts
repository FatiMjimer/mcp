import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ChatMessage {
  content: string;
  isUser: boolean;
  timestamp: Date;
}

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  // changer avec le port du client , that's it !
  private apiUrl = 'http://localhost:8066';

  constructor(private http: HttpClient) { }

  sendMessage(message: string): Observable<string> {
    const formData = new FormData();
    formData.append('message', message);

    return this.http.post(`${this.apiUrl}/chat/send`, formData, {
      responseType: 'text'
    });
  }
}
