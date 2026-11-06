# HyperLock

A modified fork of [HyperDuo](https://github.com/yixing233/HyperDuo) with a heart-shaped battery indicator and matching status-bar colors for compatible Xiaomi HyperOS devices. The original project is credited in [README.upstream.md](README.upstream.md). Both are licensed under [GPL-3.0](LICENSE).

## Download and use

Install the latest [Hyperlock](https://github.com/LXRylex/HyperLock-Extended/releases); Enable the module in a compatible modern LSPosed/Vector loader and scope it to `com.android.systemui`. Open HyperDuo, enable it, and select **Heart** under Drawing. Restart SystemUI using your module manager if needed.

The heart fills from the bottom according to battery level. Settings include normal, charging, low and full colors; a charging wave; percentage inside or beside the heart; automatic outline tint; and matching status-bar and HyperOS shade header colors. Default, Ring and Capsule styles are retained.

Tested on a Redmi Note 13 Pro 4G (emerald), HyperOS `OS3.0.305.0.WNFMIXM`, Android 16, with a libxposed API 102 loader. SystemUI classes vary by ROM; other devices may behave differently.

<table border="0">
  <tr>
    <td valign="top" width="50%">
      <!-- First File -->
      <img width="720" height="1280" alt="179314367216419154015387767243" src="https://github.com/user-attachments/assets/fb23ca92-58e0-4024-8281-bc0bfe2568cf" />
    </td>
    <td valign="top" width="50%">
      <!-- Second File -->
      <img width="720" height="1280" alt="image" src="https://github.com/user-attachments/assets/f91729a4-f28c-450c-8815-10b70705a223" />
    </td>
  </tr>
</table>
<p>
  <img src="https://github.com/user-attachments/assets/51187cda-b9fb-4834-a5d2-64d04fccf4be" width="24%" alt="Screenshot 1" />
  <img src="https://github.com/user-attachments/assets/6d2dca3d-40e3-45b4-9d5f-a775e8c43bc4" width="24%" alt="Screenshot 2" />
  <img src="https://github.com/user-attachments/assets/1456da4a-43bb-4c65-8de2-de21bb016148" width="24%" alt="Screenshot 3" />
  <img src="https://github.com/user-attachments/assets/dd3c3f78-77b0-4403-8e56-14751d81014b" width="24%" alt="Screenshot 4" />
</p>
