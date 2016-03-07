ZoomImageView
===

ZoomImageView is an View displaying images that supports move, zoom and fling gestures.

It is just under ***developing*** for more powerful and functional features so for now there is not a gradle import url.

You can also clone the project and I'm looking forward to discuss with you. [kyleduo@gmail.com](mailto:kyleduo@gmail.com).

Demo
---

There's a demo for you can install. [APK](./demo/zoomimageview_demo.apk)
![demo](./preview/preview1.png)


TODO
---

* **Support for Bitmap larger than 4096 \* 4096.** *(for now I just scale the Bitmap down, it is not that good.)*
* **Optimize the reponse for Double-Tap gesture.** *(for now the bitmap will take turns in there scale mode: fit-in, fit-out, pixel-to-pixel, user zoom. Unless you pinch the bitmap, a large bitmap would not scale up.)*




License
---

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	   http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.