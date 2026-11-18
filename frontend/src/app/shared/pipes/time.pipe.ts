import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'time',
  standalone: true
})
export class TimePipe implements PipeTransform {
  private readonly formatter = new Intl.DateTimeFormat('fr-FR', { hour: '2-digit', minute: '2-digit' });

  transform(value: string | Date | null | undefined): string {
    if (!value) {
      return '';
    }

    const date = value instanceof Date ? value : new Date(value);
    return Number.isNaN(date.getTime()) ? '' : this.formatter.format(date);
  }
}
