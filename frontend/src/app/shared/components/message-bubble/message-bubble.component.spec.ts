import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageDto } from '../../../core/models';
import { MessageBubbleComponent } from './message-bubble.component';

describe('MessageBubbleComponent', () => {
  let fixture: ComponentFixture<MessageBubbleComponent>;
  let component: MessageBubbleComponent;

  const message: MessageDto = {
    id: 5,
    conversationId: 1,
    senderId: 1,
    senderName: 'Alice Martin',
    content: 'Bonjour à tous',
    createdAt: '2026-11-18T10:30:00Z',
    status: 'READ',
    clientId: null
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [MessageBubbleComponent] }).compileComponents();
    fixture = TestBed.createComponent(MessageBubbleComponent);
    component = fixture.componentInstance;
    component.message = message;
  });

  it('renders the message content and author', () => {
    component.sent = false;
    component.showAuthor = true;
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Bonjour à tous');
    expect(element.textContent).toContain('Alice Martin');
    expect(element.querySelector('.bubble--sent')).toBeNull();
  });

  it('renders a sent message with read receipts', () => {
    component.sent = true;
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.message-row--sent')).not.toBeNull();
    expect(element.querySelector('.bubble--sent')).not.toBeNull();
    expect(element.textContent).toContain('done_all');
    expect(component.statusLabel).toBe('Lu');
  });
});
