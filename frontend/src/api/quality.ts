import { get } from './request'
import type {
  QualityOverview, DailyRatingTrendItem, LowRatedMessage,
  BlindSpotItem, DepartmentQualityItem
} from '@/types'

export const getQualityOverview = () =>
  get<QualityOverview>('/quality/overview')

export const getQualityTrend = (days: number = 30) =>
  get<DailyRatingTrendItem[]>('/quality/trend', { days })

export const getLowRatedMessages = (limit: number = 20) =>
  get<LowRatedMessage[]>('/quality/low-rated', { limit })

export const getBlindSpots = (limit: number = 20) =>
  get<BlindSpotItem[]>('/quality/blind-spots', { limit })

export const getDepartmentQualityStats = () =>
  get<DepartmentQualityItem[]>('/quality/department-stats')
