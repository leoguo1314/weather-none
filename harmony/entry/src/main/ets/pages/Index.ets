import { http } from '@kit.NetworkKit';

class CityOption {
  name: string;
  latitude: number;
  longitude: number;

  constructor(name: string, latitude: number, longitude: number) {
    this.name = name;
    this.latitude = latitude;
    this.longitude = longitude;
  }
}

class ForecastDay {
  date: string;
  weatherCode: number;
  high: number;
  low: number;
  precipitationProbability: number;

  constructor(date: string, weatherCode: number, high: number, low: number,
    precipitationProbability: number) {
    this.date = date;
    this.weatherCode = weatherCode;
    this.high = high;
    this.low = low;
    this.precipitationProbability = precipitationProbability;
  }
}

interface OpenMeteoCurrent {
  temperature_2m: number;
  relative_humidity_2m: number;
  apparent_temperature: number;
  precipitation: number;
  weather_code: number;
  pressure_msl: number;
  wind_speed_10m: number;
  wind_direction_10m: number;
}

interface OpenMeteoDaily {
  time: string[];
  weather_code: number[];
  temperature_2m_max: number[];
  temperature_2m_min: number[];
  precipitation_probability_max: number[];
}

interface OpenMeteoResponse {
  current: OpenMeteoCurrent;
  daily: OpenMeteoDaily;
}

interface MetNorwayDetails {
  air_temperature?: number;
  relative_humidity?: number;
  air_pressure_at_sea_level?: number;
  wind_speed?: number;
  wind_from_direction?: number;
}

interface MetNorwayInstant {
  details?: MetNorwayDetails;
}

interface MetNorwaySummary {
  symbol_code?: string;
}

interface MetNorwayPeriodDetails {
  precipitation_amount?: number;
  probability_of_precipitation?: number;
}

interface MetNorwayPeriod {
  summary?: MetNorwaySummary;
  details?: MetNorwayPeriodDetails;
}

interface MetNorwayData {
  instant?: MetNorwayInstant;
  next_1_hours?: MetNorwayPeriod;
  next_6_hours?: MetNorwayPeriod;
  next_12_hours?: MetNorwayPeriod;
}

interface MetNorwayTimeSeries {
  time?: string;
  data?: MetNorwayData;
}

interface MetNorwayProperties {
  timeseries?: MetNorwayTimeSeries[];
}

interface MetNorwayResponse {
  properties?: MetNorwayProperties;
}

interface CaiyunWind {
  speed?: number;
  direction?: number;
}

interface CaiyunLocalPrecipitation {
  intensity?: number;
}

interface CaiyunPrecipitation {
  local?: CaiyunLocalPrecipitation;
}

interface CaiyunRealtime {
  temperature?: number;
  apparent_temperature?: number;
  humidity?: number;
  skycon?: string;
  pressure?: number;
  wind?: CaiyunWind;
  precipitation?: CaiyunPrecipitation;
}

interface CaiyunDailyTemperature {
  date?: string;
  max?: number;
  min?: number;
}

interface CaiyunDailySkycon {
  date?: string;
  value?: string;
}

interface CaiyunDailyPrecipitation {
  date?: string;
  probability?: number;
}

interface CaiyunDaily {
  temperature?: CaiyunDailyTemperature[];
  skycon?: CaiyunDailySkycon[];
  precipitation?: CaiyunDailyPrecipitation[];
}

interface CaiyunResult {
  realtime?: CaiyunRealtime;
  daily?: CaiyunDaily;
}

interface CaiyunResponse {
  status?: string;
  result?: CaiyunResult;
}

class SourceOption {
  id: string;
  name: string;
  description: string;

  constructor(id: string, name: string, description: string) {
    this.id = id;
    this.name = name;
    this.description = description;
  }
}

const SOURCE_OPEN_METEO: string = 'open_meteo';
const SOURCE_MET_NORWAY: string = 'met_norway';
const SOURCE_CAIYUN: string = 'caiyun';
const SOURCE_CUSTOM: string = 'custom';

const WEATHER_SOURCES: SourceOption[] = [
  new SourceOption(SOURCE_OPEN_METEO, 'Open-Meteo', '免密钥，全球多模型预报，默认推荐'),
  new SourceOption(SOURCE_MET_NORWAY, 'MET Norway', '免密钥，挪威气象研究所全球九日预报'),
  new SourceOption(SOURCE_CAIYUN, '彩云天气', '需要 Token，中国地区分钟级能力更完整')
];

const DEFAULT_CUSTOM_URL: string = 'https://api.open-meteo.com/v1/forecast' +
  '?latitude={lat}&longitude={lon}' +
  '&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,' +
  'weather_code,pressure_msl,wind_speed_10m,wind_direction_10m' +
  '&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max' +
  '&forecast_days={days}&timezone=auto';

PersistentStorage.persistProp('weatherSource', SOURCE_OPEN_METEO);
PersistentStorage.persistProp('customSourceName', '自定义天气源');
PersistentStorage.persistProp('customSourceUrl', DEFAULT_CUSTOM_URL);

const CITIES: CityOption[] = [
  new CityOption('北京', 39.9042, 116.4074),
  new CityOption('上海', 31.2304, 121.4737),
  new CityOption('深圳', 22.5431, 114.0579),
  new CityOption('广州', 23.1291, 113.2644),
  new CityOption('新加坡', 1.3521, 103.8198)
];

function weatherText(code: number): string {
  if (code === 0) {
    return '晴';
  }
  if (code === 1 || code === 2) {
    return '晴间多云';
  }
  if (code === 3) {
    return '阴';
  }
  if (code === 45 || code === 48) {
    return '雾';
  }
  if (code >= 51 && code <= 57) {
    return '毛毛雨';
  }
  if (code >= 61 && code <= 67) {
    return '降雨';
  }
  if (code >= 71 && code <= 77) {
    return '降雪';
  }
  if (code >= 80 && code <= 82) {
    return '阵雨';
  }
  if (code >= 85 && code <= 86) {
    return '阵雪';
  }
  if (code >= 95) {
    return '雷暴';
  }
  return '天气变化';
}

function weatherIcon(code: number): string {
  if (code === 0) {
    return '☀️';
  }
  if (code <= 3) {
    return '⛅';
  }
  if (code === 45 || code === 48) {
    return '🌫️';
  }
  if (code >= 71 && code <= 77 || code >= 85 && code <= 86) {
    return '🌨️';
  }
  if (code >= 95) {
    return '⛈️';
  }
  if (code >= 51 && code <= 82) {
    return '🌧️';
  }
  return '🌤️';
}

function caiyunSkyconToCode(skycon: string): number {
  if (skycon.includes('THUNDER')) {
    return 95;
  }
  if (skycon.includes('SLEET')) {
    return 67;
  }
  if (skycon.includes('SNOW')) {
    return skycon.includes('HEAVY') || skycon.includes('STORM') ? 75 : 71;
  }
  if (skycon.includes('RAIN')) {
    if (skycon.includes('HEAVY') || skycon.includes('STORM')) {
      return 65;
    }
    return skycon.includes('MODERATE') ? 63 : 61;
  }
  if (skycon.includes('HAZE') || skycon === 'FOG' || skycon === 'DUST' || skycon === 'SAND') {
    return 45;
  }
  if (skycon === 'CLOUDY') {
    return 3;
  }
  if (skycon.includes('PARTLY_CLOUDY')) {
    return 2;
  }
  if (skycon.includes('CLEAR')) {
    return 0;
  }
  return 3;
}

function metSymbolToCode(symbol: string): number {
  const value: string = symbol.toLowerCase();
  if (value.includes('thunder')) {
    return 95;
  }
  if (value.includes('sleet')) {
    return 67;
  }
  if (value.includes('snow')) {
    return value.includes('heavy') ? 75 : 71;
  }
  if (value.includes('rain') || value.includes('drizzle')) {
    return value.includes('heavy') ? 65 : 61;
  }
  if (value.includes('fog')) {
    return 45;
  }
  if (value.includes('partlycloudy') || value.includes('fair')) {
    return 2;
  }
  if (value.includes('clearsky')) {
    return 0;
  }
  return 3;
}

function metPreferredPeriod(item: MetNorwayTimeSeries): MetNorwayPeriod | undefined {
  if (item.data === undefined) {
    return undefined;
  }
  return item.data.next_1_hours ?? item.data.next_6_hours ?? item.data.next_12_hours;
}

function directionText(degree: number): string {
  const directions: string[] = ['北', '东北', '东', '东南', '南', '西南', '西', '西北'];
  const index: number = Math.round(degree / 45) % 8;
  return directions[index];
}

@Entry
@Component
struct Index {
  @State cityIndex: number = 0;
  @State activeTab: number = 0;
  @StorageLink('weatherSource') weatherSource: string = SOURCE_OPEN_METEO;
  @StorageLink('customSourceName') customSourceName: string = '自定义天气源';
  @StorageLink('customSourceUrl') customSourceUrl: string = DEFAULT_CUSTOM_URL;
  @State caiyunToken: string = '';
  @State customSecret: string = '';
  @State sourceTestMessage: string = '';
  @State loading: boolean = true;
  @State loadError: string = '';
  @State temperature: number = 0;
  @State apparentTemperature: number = 0;
  @State humidity: number = 0;
  @State precipitation: number = 0;
  @State weatherCode: number = 0;
  @State pressure: number = 0;
  @State windSpeed: number = 0;
  @State windDirection: number = 0;
  @State forecast: ForecastDay[] = [];
  @State question: string = '';
  @State agentAnswer: string = '我会根据当前实时天气，给出穿衣、带伞和出行建议。';
  @State toolTrace: string[] = [];
  private requestSerial: number = 0;

  aboutToAppear(): void {
    this.loadWeather();
  }

  private currentCity(): CityOption {
    return CITIES[this.cityIndex];
  }

  private sourceDisplayName(): string {
    if (this.weatherSource === SOURCE_CUSTOM) {
      return this.customSourceName.trim().length > 0 ? this.customSourceName.trim() : '自定义天气源';
    }
    const source: SourceOption | undefined = WEATHER_SOURCES.find((item: SourceOption) =>
      item.id === this.weatherSource);
    return source !== undefined ? source.name : 'Open-Meteo';
  }

  private buildSourceUrl(city: CityOption): string {
    const lat: string = city.latitude.toString();
    const lon: string = city.longitude.toString();
    if (this.weatherSource === SOURCE_MET_NORWAY) {
      return `https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=${lat}&lon=${lon}`;
    }
    if (this.weatherSource === SOURCE_CAIYUN) {
      const token: string = this.caiyunToken.trim();
      if (token.length === 0) {
        throw new Error('请先在天气源设置中填写彩云 Token');
      }
      return `https://wrapper.cyapi.cn/v2.7/${encodeURIComponent(token)}/${lon},${lat}/weather` +
        '?span=16&alert=false&hourlysteps=24&lang=zh_CN&version=7.59.0';
    }
    if (this.weatherSource === SOURCE_CUSTOM) {
      const template: string = this.customSourceUrl.trim();
      if (!template.startsWith('https://') || !template.includes('{lat}') || !template.includes('{lon}')) {
        throw new Error('自定义 URL 必须使用 HTTPS，并包含 {lat} 和 {lon}');
      }
      return template
        .replace('{lat}', lat)
        .replace('{lon}', lon)
        .replace('{days}', '7')
        .replace('{key}', encodeURIComponent(this.customSecret));
    }
    return DEFAULT_CUSTOM_URL
      .replace('{lat}', lat)
      .replace('{lon}', lon)
      .replace('{days}', '7');
  }

  private async loadWeather(testSource: boolean = false): Promise<void> {
    const requestSerial: number = ++this.requestSerial;
    this.loading = true;
    this.loadError = '';
    if (testSource) {
      this.sourceTestMessage = '正在测试连接…';
    }
    const city: CityOption = this.currentCity();
    const request: http.HttpRequest = http.createHttp();
    try {
      const url: string = this.buildSourceUrl(city);
      const response: http.HttpResponse = await request.request(url, {
        method: http.RequestMethod.GET,
        header: {
          'User-Agent': 'SkyPulse-Weather/4.1.1 (github.com/leoguo1314/weather-none)'
        },
        connectTimeout: 15000,
        readTimeout: 15000,
        expectDataType: http.HttpDataType.STRING
      });
      if (response.responseCode !== 200) {
        throw new Error(`天气服务返回 ${response.responseCode}`);
      }
      const raw: string = response.result as string;
      if (requestSerial !== this.requestSerial) {
        return;
      }
      if (this.weatherSource === SOURCE_MET_NORWAY) {
        this.applyMetNorway(JSON.parse(raw) as MetNorwayResponse);
      } else if (this.weatherSource === SOURCE_CAIYUN) {
        this.applyCaiyun(JSON.parse(raw) as CaiyunResponse);
      } else {
        this.applyOpenMeteo(JSON.parse(raw) as OpenMeteoResponse);
      }
      this.agentAnswer = `${city.name}天气已由 ${this.sourceDisplayName()} 更新。` +
        '你可以问我未来趋势、穿衣、带伞或户外活动风险。';
      if (testSource) {
        this.sourceTestMessage = `${this.sourceDisplayName()} 连接成功，${this.forecast.length} 日预报可用`;
      }
    } catch (error) {
      if (requestSerial === this.requestSerial) {
        this.loadError = `加载失败：${error instanceof Error ? error.message : '网络异常'}`;
        if (testSource) {
          this.sourceTestMessage = this.loadError;
        }
      }
    } finally {
      request.destroy();
      if (requestSerial === this.requestSerial) {
        this.loading = false;
      }
    }
  }

  private applyOpenMeteo(data: OpenMeteoResponse): void {
    if (data.current === undefined || data.daily === undefined || data.daily.time.length === 0) {
      throw new Error('天气源缺少 current 或 daily 数据');
    }
    this.temperature = data.current.temperature_2m;
    this.apparentTemperature = data.current.apparent_temperature;
    this.humidity = data.current.relative_humidity_2m;
    this.precipitation = data.current.precipitation;
    this.weatherCode = data.current.weather_code;
    this.pressure = data.current.pressure_msl;
    this.windSpeed = data.current.wind_speed_10m;
    this.windDirection = data.current.wind_direction_10m;

    const days: ForecastDay[] = [];
    const count: number = Math.min(7, data.daily.time.length);
    for (let index: number = 0; index < count; index++) {
      days.push(new ForecastDay(
        data.daily.time[index],
        data.daily.weather_code[index],
        data.daily.temperature_2m_max[index],
        data.daily.temperature_2m_min[index],
        data.daily.precipitation_probability_max[index]
      ));
    }
    this.forecast = days;
  }

  private applyCaiyun(data: CaiyunResponse): void {
    const result: CaiyunResult | undefined = data.result;
    if (data.status !== 'ok' || result === undefined ||
      result.realtime === undefined || result.daily === undefined) {
      throw new Error(`彩云天气响应异常：${data.status ?? 'empty'}`);
    }
    const realtime: CaiyunRealtime = result.realtime;
    const daily: CaiyunDaily = result.daily;
    this.temperature = realtime.temperature ?? 0;
    this.apparentTemperature = realtime.apparent_temperature ?? this.temperature;
    const humidity: number = realtime.humidity ?? 0;
    this.humidity = humidity <= 1 ? humidity * 100 : humidity;
    this.precipitation = realtime.precipitation?.local?.intensity ?? 0;
    this.weatherCode = caiyunSkyconToCode(realtime.skycon ?? 'CLOUDY');
    const pressure: number = realtime.pressure ?? 0;
    this.pressure = pressure > 2000 ? pressure / 100 : pressure;
    this.windSpeed = realtime.wind?.speed ?? 0;
    this.windDirection = realtime.wind?.direction ?? 0;

    const temperatures: CaiyunDailyTemperature[] = daily.temperature ?? [];
    const skycons: CaiyunDailySkycon[] = daily.skycon ?? [];
    const precipitation: CaiyunDailyPrecipitation[] = daily.precipitation ?? [];
    const days: ForecastDay[] = [];
    const count: number = Math.min(7, temperatures.length);
    for (let index: number = 0; index < count; index++) {
      const probabilityRaw: number = precipitation[index]?.probability ?? 0;
      days.push(new ForecastDay(
        temperatures[index].date ?? '',
        caiyunSkyconToCode(skycons[index]?.value ?? 'CLOUDY'),
        temperatures[index].max ?? 0,
        temperatures[index].min ?? 0,
        probabilityRaw <= 1 ? probabilityRaw * 100 : probabilityRaw
      ));
    }
    if (days.length === 0) {
      throw new Error('彩云天气未返回日预报');
    }
    this.forecast = days;
  }

  private applyMetNorway(data: MetNorwayResponse): void {
    const entries: MetNorwayTimeSeries[] = data.properties?.timeseries ?? [];
    if (entries.length === 0 || entries[0].data?.instant?.details === undefined) {
      throw new Error('MET Norway 未返回预报');
    }
    const current: MetNorwayDetails = entries[0].data?.instant?.details as MetNorwayDetails;
    const currentPeriod: MetNorwayPeriod | undefined = metPreferredPeriod(entries[0]);
    this.temperature = current.air_temperature ?? 0;
    this.apparentTemperature = this.temperature;
    this.humidity = current.relative_humidity ?? 0;
    this.precipitation = currentPeriod?.details?.precipitation_amount ?? 0;
    this.weatherCode = metSymbolToCode(currentPeriod?.summary?.symbol_code ?? 'cloudy');
    this.pressure = current.air_pressure_at_sea_level ?? 0;
    this.windSpeed = (current.wind_speed ?? 0) * 3.6;
    this.windDirection = current.wind_from_direction ?? 0;

    const dates: string[] = [];
    for (let index: number = 0; index < entries.length; index++) {
      const time: string = entries[index].time ?? '';
      const date: string = time.substring(0, Math.min(10, time.length));
      if (date.length === 10 && !dates.includes(date)) {
        dates.push(date);
      }
      if (dates.length >= 7) {
        break;
      }
    }

    const days: ForecastDay[] = [];
    for (let dateIndex: number = 0; dateIndex < dates.length; dateIndex++) {
      const date: string = dates[dateIndex];
      let high: number = -1000;
      let low: number = 1000;
      let probability: number = 0;
      let code: number = 3;
      let found: boolean = false;
      for (let index: number = 0; index < entries.length; index++) {
        if (!(entries[index].time ?? '').startsWith(date)) {
          continue;
        }
        const details: MetNorwayDetails | undefined = entries[index].data?.instant?.details;
        if (details?.air_temperature !== undefined) {
          high = Math.max(high, details.air_temperature);
          low = Math.min(low, details.air_temperature);
          found = true;
        }
        const period: MetNorwayPeriod | undefined = metPreferredPeriod(entries[index]);
        probability = Math.max(probability, period?.details?.probability_of_precipitation ?? 0);
        if (period?.summary?.symbol_code !== undefined) {
          code = metSymbolToCode(period.summary.symbol_code);
        }
      }
      if (found) {
        days.push(new ForecastDay(date, code, high, low, probability));
      }
    }
    if (days.length === 0) {
      throw new Error('MET Norway 未返回日预报');
    }
    this.forecast = days;
  }

  private selectCity(index: number): void {
    if (index === this.cityIndex) {
      return;
    }
    this.cityIndex = index;
    this.forecast = [];
    this.toolTrace = [];
    this.question = '';
    this.agentAnswer = `正在加载 ${this.currentCity().name} 的实时天气…`;
    this.loadWeather();
  }

  private selectWeatherSource(source: string): void {
    if (source === this.weatherSource) {
      return;
    }
    this.weatherSource = source;
    this.sourceTestMessage = '';
    this.forecast = [];
    this.toolTrace = [];
    this.question = '';
    this.loading = false;
    this.loadError = '天气源已切换，请在天气源页点击“测试并应用”';
    this.agentAnswer = `已切换到 ${this.sourceDisplayName()}，请测试并应用。`;
  }

  private askAgent(preset?: string): void {
    if (this.loading || this.loadError.length > 0) {
      this.toolTrace = [];
      this.agentAnswer = '天气数据尚未就绪，请先完成刷新后再提问。';
      return;
    }
    const input: string = (preset ?? this.question).trim();
    if (input.length === 0) {
      return;
    }
    if (preset !== undefined) {
      this.question = preset;
    }

    const traces: string[] = ['weather.query'];
    const lower: string = input.toLowerCase();
    if (lower.includes('明天') || lower.includes('未来') || lower.includes('趋势') || lower.includes('一周')) {
      traces.push('forecast.query');
    }
    if (lower.includes('穿') || lower.includes('衣') || lower.includes('带伞') || lower.includes('防晒')) {
      traces.push('clothing.advice');
    }
    if (lower.includes('出门') || lower.includes('出行') || lower.includes('跑步') || lower.includes('户外')) {
      traces.push('travel.advice');
    }

    const advice: string[] = [];
    advice.push(`${this.currentCity().name}当前${weatherText(this.weatherCode)}，${Math.round(this.temperature)}℃，` +
      `体感${Math.round(this.apparentTemperature)}℃，湿度${Math.round(this.humidity)}%。`);

    if (this.apparentTemperature <= 8) {
      advice.push('体感较冷，建议厚外套并注意保暖。');
    } else if (this.apparentTemperature <= 18) {
      advice.push('建议长袖搭配薄外套，早晚注意温差。');
    } else if (this.apparentTemperature >= 32) {
      advice.push('体感炎热，建议轻薄速干衣物、补水并减少正午暴晒。');
    } else {
      advice.push('体感舒适，轻薄衣物即可。');
    }

    const firstDay: ForecastDay | undefined = this.forecast.length > 0 ? this.forecast[0] : undefined;
    const rainLikely: boolean = this.precipitation > 0 ||
      this.weatherCode >= 51 && this.weatherCode <= 82 ||
      firstDay !== undefined && firstDay.precipitationProbability >= 40;
    if (rainLikely) {
      advice.push('有降水可能，建议带伞、穿防滑鞋并预留通勤时间。');
    } else {
      advice.push('目前降水风险较低，可正常安排通勤和户外活动。');
    }

    if (this.windSpeed >= 30) {
      advice.push('风力较强，避免在广告牌、临时搭建物和大树附近久留。');
    }

    if (traces.includes('forecast.query') && this.forecast.length > 1) {
      const trend: string = this.forecast.slice(0, 4).map((day: ForecastDay) =>
        `${day.date.substring(5)} ${weatherText(day.weatherCode)} ${Math.round(day.low)}~${Math.round(day.high)}℃`
      ).join('；');
      advice.push(`未来趋势：${trend}。`);
    }

    this.toolTrace = traces;
    this.agentAnswer = advice.join('\n');
  }

  @Builder
  private tabHeader(): void {
    Row({ space: 8 }) {
      Button('天气')
        .layoutWeight(1)
        .height(42)
        .fontSize(13)
        .fontColor(this.activeTab === 0 ? '#153B70' : '#FFFFFF')
        .backgroundColor(this.activeTab === 0 ? '#FFFFFF' : '#33FFFFFF')
        .onClick(() => this.activeTab = 0)
      Button('AI 助手')
        .layoutWeight(1)
        .height(42)
        .fontSize(13)
        .fontColor(this.activeTab === 1 ? '#153B70' : '#FFFFFF')
        .backgroundColor(this.activeTab === 1 ? '#FFFFFF' : '#33FFFFFF')
        .onClick(() => this.activeTab = 1)
      Button('天气源')
        .layoutWeight(1)
        .height(42)
        .fontSize(13)
        .fontColor(this.activeTab === 2 ? '#153B70' : '#FFFFFF')
        .backgroundColor(this.activeTab === 2 ? '#FFFFFF' : '#33FFFFFF')
        .onClick(() => this.activeTab = 2)
    }
    .width('100%')
  }

  @Builder
  private citySelector(): void {
    Scroll() {
      Row({ space: 8 }) {
        ForEach(CITIES, (city: CityOption, index: number) => {
          Button(city.name)
            .fontSize(14)
            .height(36)
            .fontColor(index === this.cityIndex ? '#163B6D' : '#FFFFFF')
            .backgroundColor(index === this.cityIndex ? '#FFFFFF' : '#26FFFFFF')
            .onClick(() => this.selectCity(index))
        }, (city: CityOption) => city.name)
      }
    }
    .scrollable(ScrollDirection.Horizontal)
    .width('100%')
  }

  @Builder
  private weatherPage(): void {
    Scroll() {
      Column({ space: 14 }) {
        this.citySelector()

        if (this.loading) {
          Column({ space: 14 }) {
            LoadingProgress().width(42).height(42).color('#FFFFFF')
            Text('正在获取实时天气…').fontColor('#FFFFFF')
          }
          .width('100%')
          .height(220)
          .justifyContent(FlexAlign.Center)
        } else if (this.loadError.length > 0) {
          Column({ space: 14 }) {
            Text(this.loadError).fontColor('#FFF1F1').fontSize(16)
            Button('重新加载').onClick(() => this.loadWeather())
          }
          .width('100%')
          .height(220)
          .justifyContent(FlexAlign.Center)
        } else {
          Column({ space: 8 }) {
            Text(this.currentCity().name)
              .fontSize(26)
              .fontWeight(FontWeight.Bold)
              .fontColor('#FFFFFF')
            Text(`${weatherIcon(this.weatherCode)} ${weatherText(this.weatherCode)}`)
              .fontSize(22)
              .fontColor('#FFFFFF')
            Text(`数据源：${this.sourceDisplayName()}`)
              .fontSize(13)
              .fontColor('#DDEEFF')
            Text(`${Math.round(this.temperature)}°`)
              .fontSize(82)
          .fontWeight(FontWeight.Lighter)
              .fontColor('#FFFFFF')
            Text(`体感 ${Math.round(this.apparentTemperature)}℃`)
              .fontSize(16)
              .fontColor('#DDEEFF')
          }
          .width('100%')
          .padding(22)
          .backgroundColor('#20FFFFFF')
          .borderRadius(26)

          Row({ space: 10 }) {
            this.metricCard('湿度', `${Math.round(this.humidity)}%`)
            this.metricCard('风速', `${Math.round(this.windSpeed)} km/h`)
          }
          Row({ space: 10 }) {
            this.metricCard('风向', `${directionText(this.windDirection)}风`)
            this.metricCard('气压', `${Math.round(this.pressure)} hPa`)
          }

          Column({ space: 4 }) {
            Text('7 日预报')
              .fontSize(19)
              .fontWeight(FontWeight.Bold)
              .fontColor('#17385F')
              .margin({ bottom: 8 })
            ForEach(this.forecast, (day: ForecastDay, index: number) => {
              Row() {
                Text(index === 0 ? '今天' : day.date.substring(5))
                  .width(70)
                  .fontColor('#233B5A')
                Text(`${weatherIcon(day.weatherCode)} ${weatherText(day.weatherCode)}`)
                  .layoutWeight(1)
                  .fontColor('#233B5A')
                Text(`${Math.round(day.low)}° / ${Math.round(day.high)}°`)
                  .fontColor('#233B5A')
                Text(` ${Math.round(day.precipitationProbability)}%`)
                  .width(52)
                  .textAlign(TextAlign.End)
                  .fontColor('#4E6B8F')
              }
              .width('100%')
              .height(45)
              .alignItems(VerticalAlign.Center)
            }, (day: ForecastDay) => day.date)
          }
          .width('100%')
          .padding(18)
          .backgroundColor('#F2FFFFFF')
          .borderRadius(24)
        }
      }
      .width('100%')
      .padding({ bottom: 28 })
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private metricCard(label: string, value: string): void {
    Column({ space: 5 }) {
      Text(label).fontSize(13).fontColor('#DDEEFF')
      Text(value).fontSize(18).fontWeight(FontWeight.Medium).fontColor('#FFFFFF')
    }
    .layoutWeight(1)
    .padding(15)
    .backgroundColor('#24FFFFFF')
    .borderRadius(18)
  }

  @Builder
  private agentPage(): void {
    Scroll() {
      Column({ space: 14 }) {
        Column({ space: 8 }) {
          Text('SkyPulse Weather Agent')
            .fontSize(22)
            .fontWeight(FontWeight.Bold)
            .fontColor('#FFFFFF')
          Text(`正在使用 ${this.currentCity().name} 的实时天气工具`)
            .fontSize(14)
            .fontColor('#DDEEFF')
          Text(`数据源：${this.sourceDisplayName()}`)
            .fontSize(12)
            .fontColor('#C9E2F7')
        }
        .width('100%')

        Column({ space: 8 }) {
          Text('快捷问题').fontSize(16).fontWeight(FontWeight.Bold).fontColor('#17385F')
          this.suggestionButton('明天适合户外跑步吗？')
          this.suggestionButton('今天怎么穿，需要带伞吗？')
          this.suggestionButton('未来四天天气趋势')
        }
        .width('100%')
        .padding(16)
        .backgroundColor('#F0FFFFFF')
        .borderRadius(22)

        Column({ space: 10 }) {
          Text('AI 分析').fontSize(16).fontWeight(FontWeight.Bold).fontColor('#17385F')
          Text(this.agentAnswer)
            .fontSize(16)
            .lineHeight(25)
            .fontColor('#213B5A')
          if (this.toolTrace.length > 0) {
            Text(`已调用：${this.toolTrace.join(' → ')}`)
              .fontSize(12)
              .fontColor('#55718F')
              .margin({ top: 4 })
          }
        }
        .width('100%')
        .padding(18)
        .backgroundColor('#F5FFFFFF')
        .borderRadius(22)

        Row({ space: 8 }) {
          TextInput({ text: this.question, placeholder: '问天气、穿衣或出行风险' })
            .layoutWeight(1)
            .height(48)
            .backgroundColor('#F5FFFFFF')
            .borderRadius(16)
            .onChange((value: string) => this.question = value)
          Button('发送')
            .height(48)
            .backgroundColor('#1768C4')
            .onClick(() => this.askAgent())
        }
        .width('100%')
      }
      .width('100%')
      .padding({ bottom: 28 })
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private suggestionButton(text: string): void {
    Button(text)
      .width('100%')
      .height(42)
      .fontSize(14)
      .fontColor('#21507C')
      .backgroundColor('#E8F2FF')
      .onClick(() => this.askAgent(text))
  }

  @Builder
  private sourceSettingsPage(): void {
    Scroll() {
      Column({ space: 14 }) {
        Column({ space: 8 }) {
          Text('三个内置天气源')
            .fontSize(19)
            .fontWeight(FontWeight.Bold)
            .fontColor('#17385F')
          ForEach(WEATHER_SOURCES, (source: SourceOption) => {
            Button(`${this.weatherSource === source.id ? '✓ ' : ''}${source.name}`)
              .width('100%')
              .height(44)
              .fontColor(this.weatherSource === source.id ? '#FFFFFF' : '#21507C')
              .backgroundColor(this.weatherSource === source.id ? '#1768C4' : '#E8F2FF')
              .onClick(() => this.selectWeatherSource(source.id))
            Text(source.description)
              .fontSize(12)
              .fontColor('#55718F')
          }, (source: SourceOption) => source.id)
        }
        .width('100%')
        .padding(16)
        .backgroundColor('#F2FFFFFF')
        .borderRadius(22)

        if (this.weatherSource === SOURCE_CAIYUN) {
          Column({ space: 8 }) {
            Text('彩云 Token（仅本次运行保留）')
              .fontSize(14)
              .fontColor('#17385F')
            TextInput({ text: this.caiyunToken, placeholder: '请输入彩云天气 Token' })
              .width('100%')
              .height(48)
              .type(InputType.Password)
              .backgroundColor('#FFFFFF')
              .borderRadius(14)
              .onChange((value: string) => {
                this.caiyunToken = value;
                this.sourceTestMessage = '';
              })
          }
          .width('100%')
          .padding(16)
          .backgroundColor('#F2FFFFFF')
          .borderRadius(22)
        }

        Column({ space: 8 }) {
          Button(`${this.weatherSource === SOURCE_CUSTOM ? '✓ ' : ''}自定义兼容源`)
            .width('100%')
            .height(44)
            .fontColor(this.weatherSource === SOURCE_CUSTOM ? '#FFFFFF' : '#21507C')
            .backgroundColor(this.weatherSource === SOURCE_CUSTOM ? '#1768C4' : '#E8F2FF')
            .onClick(() => this.selectWeatherSource(SOURCE_CUSTOM))
          Text('使用 Open-Meteo 兼容 JSON；URL 必须包含 {lat}、{lon}，可选 {days}、{key}')
            .fontSize(12)
            .lineHeight(18)
            .fontColor('#55718F')

          if (this.weatherSource === SOURCE_CUSTOM) {
            TextInput({ text: this.customSourceName, placeholder: '天气源显示名称' })
              .width('100%')
              .height(48)
              .backgroundColor('#FFFFFF')
              .borderRadius(14)
              .onChange((value: string) => {
                this.customSourceName = value;
                this.sourceTestMessage = '';
              })
            TextArea({ text: this.customSourceUrl, placeholder: 'HTTPS URL 模板' })
              .width('100%')
              .height(126)
              .backgroundColor('#FFFFFF')
              .borderRadius(14)
              .onChange((value: string) => {
                this.customSourceUrl = value;
                this.sourceTestMessage = '';
              })
            TextInput({ text: this.customSecret, placeholder: '可选密钥，对应 URL 中的 {key}' })
              .width('100%')
              .height(48)
              .type(InputType.Password)
              .backgroundColor('#FFFFFF')
              .borderRadius(14)
              .onChange((value: string) => {
                this.customSecret = value;
                this.sourceTestMessage = '';
              })
            Text('敏感值不会明文持久化，重启后需重新输入。')
              .fontSize(12)
              .fontColor('#8A5B27')
          }
        }
        .width('100%')
        .padding(16)
        .backgroundColor('#F2FFFFFF')
        .borderRadius(22)

        if (this.sourceTestMessage.length > 0) {
          Text(this.sourceTestMessage)
            .width('100%')
            .fontSize(14)
            .lineHeight(21)
            .fontColor(this.sourceTestMessage.includes('成功') ? '#1768C4' : '#9B3A3A')
            .padding(14)
            .backgroundColor('#E8FFFFFF')
            .borderRadius(16)
        }

        Button(this.loading ? '正在测试…' : '测试并应用')
          .width('100%')
          .height(48)
          .enabled(!this.loading)
          .fontColor('#FFFFFF')
          .backgroundColor('#1768C4')
          .onClick(() => this.loadWeather(true))
      }
      .width('100%')
      .padding({ bottom: 28 })
    }
    .width('100%')
    .layoutWeight(1)
  }

  build() {
    Column({ space: 14 }) {
      Row() {
        Column({ space: 2 }) {
          Text('SkyPulse')
            .fontSize(24)
            .fontWeight(FontWeight.Bold)
            .fontColor('#FFFFFF')
          Text('AI 天气 · HarmonyOS')
            .fontSize(13)
            .fontColor('#DDEEFF')
        }
        .layoutWeight(1)
          Button('刷新')
            .height(34)
            .fontSize(12)
            .fontColor('#FFFFFF')
            .backgroundColor('#26FFFFFF')
            .enabled(!this.loading)
            .onClick(() => this.loadWeather())
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      this.tabHeader()

      if (this.activeTab === 0) {
        this.weatherPage()
      } else if (this.activeTab === 1) {
        this.agentPage()
      } else {
        this.sourceSettingsPage()
      }
    }
    .width('100%')
    .height('100%')
    .padding({ top: 18, left: 16, right: 16 })
    .linearGradient({
      angle: 180,
      colors: [['#123E75', 0.0], ['#3F83BD', 0.55], ['#96CDEB', 1.0]]
    })
  }
}
