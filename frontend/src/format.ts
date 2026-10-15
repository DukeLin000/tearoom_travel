import type { Category } from './types';
export const categories: Record<Category, string> = { FOOD: '餐飲', TRANSPORT: '交通', STAY: '住宿', ACTIVITY: '活動', SHOPPING: '購物', OTHER: '其他' };
// Format server decimal strings without converting accounting amounts to floating point.
export function money(value: string, currency: string) {
  const [integer, fraction = '00'] = value.split('.');
  return `${currency} ${integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${fraction.padEnd(2, '0')}`;
}
