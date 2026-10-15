export type User = { id: string; displayName: string };
export type Trip = {
  id: string; title: string; destination: string; startDate: string; endDate: string;
  timezone: string; currency: string; budgetLimit: string; ownerUserId: string; version: number; memberCount: number;
};
export type CreateTrip = Omit<Trip, 'id' | 'ownerUserId' | 'version' | 'memberCount'>;
export type Category = 'FOOD' | 'TRANSPORT' | 'STAY' | 'ACTIVITY' | 'SHOPPING' | 'OTHER';
export type ItemFields = {
  title: string; category: Category; localStartTime: string | null; localEndTime: string | null;
  locationName: string | null; mapUrl: string | null; estimatedAmount: string; note: string;
};
export type Item = ItemFields & { id: string; dayId: string; sortOrder: number; version: number; updatedAt: string; deletedAt: string | null };
export type Day = { id: string; localDate: string; version: number; items: Item[] };
export type Schedule = { tripId: string; days: Day[] };
export type Budget = { budgetLimit: string; plannedTotal: string; remaining: string; estimatedPerPerson: string; memberCount: number; breakdown: Partial<Record<Category, string>> };
export type TripPage = { items: Trip[]; page: number; size: number; total: number };
