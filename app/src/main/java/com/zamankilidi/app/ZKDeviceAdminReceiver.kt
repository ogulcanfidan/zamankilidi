package com.zamankilidi.app

import android.app.admin.DeviceAdminReceiver

/**
 * Sadece cihaz yöneticisi olarak kayıtlı olmak için var. Bu izin aktifken
 * Android, uygulamayı kaldırmadan önce kullanıcıyı "önce yönetici olarak
 * devre dışı bırak" demeye zorluyor - yani çocuğun uygulamayı rastgele
 * silmesi bir adım daha zorlaşıyor. Özel bir davranış eklemiyoruz, sadece
 * kayıt.
 */
class ZKDeviceAdminReceiver : DeviceAdminReceiver()
