import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'timeAgo',
  standalone: true
})
export class TimeAgoPipe implements PipeTransform {
  transform(value: string | Date | null | undefined): string {
    if (!value) {
      return '';
    }

    const date = value instanceof Date ? value : new Date(value);
    const timestamp = date.getTime();

    if (Number.isNaN(timestamp)) {
      return '';
    }

    const minutes = Math.floor((Date.now() - timestamp) / 60000);

    if (minutes < 1) {
      return "à l'instant";
    }

    if (minutes < 60) {
      return `il y a ${minutes} min`;
    }

    const hours = Math.floor(minutes / 60);

    if (hours < 24) {
      return `il y a ${hours} h`;
    }

    const days = Math.floor(hours / 24);

    if (days === 1) {
      return 'hier';
    }

    if (days < 7) {
      return `il y a ${days} jours`;
    }

    return new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' }).format(date);
  }
}
